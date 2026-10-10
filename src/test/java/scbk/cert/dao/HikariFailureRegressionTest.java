package scbk.cert.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.bean.uplus.HeaderBean;
import scbk.cert.bean.uplus.XPayAuthBean;

/** Real HikariCP and production DAOs, using only the socket-free RecordingDriver. */
class HikariFailureRegressionTest {
    private static final String TIMEOUT_PROPERTY = "scbk.cert.db.connectionTimeoutMs";
    private String previousTimeout;

    @BeforeEach
    void configureIsolatedPool() throws Exception {
        previousTimeout = System.getProperty(TIMEOUT_PROPERTY);
        DBFactory.shutdown();
        set("dataSource", null);
        set("instance", null);
        set("configBean", null);
        RecordingDriver.reset();
        RecordingDriver.legacyCommit = false;
        LegacyJdbcBaseline.installConfiguration();
        System.setProperty(TIMEOUT_PROPERTY, "250");
    }

    @AfterEach
    void closeIsolatedPool() throws Exception {
        DBFactory.shutdown();
        set("dataSource", null);
        set("instance", null);
        set("configBean", null);
        if (previousTimeout == null) System.clearProperty(TIMEOUT_PROPERTY);
        else System.setProperty(TIMEOUT_PROPERTY, previousTimeout);
    }

    @Test
    void exhaustedPoolRejectsBothProviderInsertsThenRecoversWithoutLeaks() throws Exception {
        DBFactory db = DBFactory.getInstance();
        DAO uplus = new DAO("SYNTHETIC_CERT");
        KCBDAO kcb = new KCBDAO("SYNTHETIC_CERT", "SYNTHETIC_INFO");
        XPayAuthBean uplusRequest = new XPayAuthBean();
        uplusRequest.setMobileStep("STEP1");
        KCBCertReqBean kcbRequest = new KCBCertReqBean();
        kcbRequest.setMobileStep("STEP1");
        int createdBeforeRecovery;

        try (Connection first = db.getConnection(); Connection second = db.getConnection()) {
            assertNotNull(first);
            assertNotNull(second);
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                assertFalse(uplus.insert("123", "SYN", "OFFICE", uplusRequest));
                assertFalse(kcb.insert("123", "SYN", "OFFICE", "20261010120000", kcbRequest));
            });
            assertEquals(0, RecordingDriver.executions);
            assertEquals(0, RecordingDriver.durableWrites);
            assertEquals(0, RecordingDriver.commits);
            assertTrue(RecordingDriver.connections.stream().allMatch(c -> c.statements.isEmpty()));
            assertEquals(2, pool().getHikariPoolMXBean().getActiveConnections());
            assertEquals(2, pool().getHikariPoolMXBean().getTotalConnections());
            createdBeforeRecovery = RecordingDriver.connections.size();
        }

        assertTrue(uplus.insert("123", "SYN", "OFFICE", uplusRequest));
        assertTrue(kcb.insert("123", "SYN", "OFFICE", "20261010120000", kcbRequest));
        assertEquals(2, RecordingDriver.executions);
        assertEquals(2, RecordingDriver.durableWrites);
        assertEquals(2, RecordingDriver.commits);
        assertEquals(createdBeforeRecovery, RecordingDriver.connections.size());
        assertEquals(0, pool().getHikariPoolMXBean().getActiveConnections());
        assertTrue(RecordingDriver.connections.stream()
                .flatMap(c -> c.statements.stream()).allMatch(s -> s.closed));
    }

    @Test
    void exhaustedPoolPreservesDaoFailureResultsWithoutExecutingStatements() throws Exception {
        DBFactory db = DBFactory.getInstance();
        DAO uplus = new DAO("SYNTHETIC_CERT");
        KCBDAO kcb = new KCBDAO("SYNTHETIC_CERT", "SYNTHETIC_INFO");
        try (Connection first = db.getConnection(); Connection second = db.getConnection()) {
            assertNotNull(first);
            assertNotNull(second);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                assertFalse(uplus.selectDeptCode("SYN"));
                assertFalse(kcb.selectDeptCode("SYN"));
                assertEquals("", uplus.getSerno());
                assertEquals("", kcb.getSerno());
                // These null arrays characterize existing behavior; they are not
                // approval for Gateway to treat a failed limit check as zero.
                assertArrayEquals(new String[3], uplus.selectCheck(new XPayAuthBean()));
                assertArrayEquals(new String[3], kcb.selectCheck(new KCBCertReqBean()));
                assertFalse(uplus.updateStep1("123", new HeaderBean(), new XPayAuthBean()));
                assertFalse(uplus.updateStep2("123", new HeaderBean(), new XPayAuthBean()));
                assertFalse(kcb.updateStep1("123", "20261010120000",
                        new KCBHeaderBean(), new KCBCertResBean()));
            });
            assertEquals(0, RecordingDriver.executions);
            assertEquals(0, RecordingDriver.durableWrites);
            assertTrue(RecordingDriver.connections.stream().allMatch(c -> c.statements.isEmpty()));
            assertEquals(2, pool().getHikariPoolMXBean().getActiveConnections());
        }
        try (Connection recovered = db.getConnection()) {
            assertNotNull(recovered);
            assertTrue(recovered.getAutoCommit());
        }
        assertEquals(0, pool().getHikariPoolMXBean().getActiveConnections());
    }

    @Test
    @Timeout(15)
    void concurrentBorrowersRespectPoolLimitAndReturnEveryConnection() throws Exception {
        // Avoid turning thread scheduling delays into 250 ms acquisition failures.
        System.setProperty(TIMEOUT_PROPERTY, "3000");
        DBFactory db = DBFactory.getInstance();
        int workerCount = 8;
        int iterations = 25;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(workerCount, action -> {
            Thread thread = new Thread(action, "synthetic-pool-borrower");
            thread.setDaemon(true);
            return thread;
        });
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < workerCount; i++) {
                results.add(workers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int j = 0; j < iterations; j++) {
                        try (Connection connection = db.getConnection()) {
                            assertNotNull(connection);
                            int current = active.incrementAndGet();
                            peak.accumulateAndGet(current, Math::max);
                            try {
                                assertTrue(connection.getAutoCommit());
                                assertFalse(connection.isClosed());
                                completed.incrementAndGet();
                            } finally {
                                active.decrementAndGet();
                            }
                        }
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> result : results) result.get(5, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(workerCount * iterations, completed.get());
        assertTrue(peak.get() > 0 && peak.get() <= 2);
        assertTrue(pool().getHikariPoolMXBean().getTotalConnections() > 0);
        assertTrue(pool().getHikariPoolMXBean().getTotalConnections() <= 2);
        // With minimumIdle=0, startup may create and close a validation connection.
        // The configured limit applies to live pool connections, not lifetime creations.
        assertTrue(RecordingDriver.connections.stream().filter(c -> !c.closed).count() <= 2);
        assertEquals(0, active.get());
        assertEquals(0, pool().getHikariPoolMXBean().getActiveConnections());
        assertEquals(0, pool().getHikariPoolMXBean().getThreadsAwaitingConnection());
        assertEquals(0, RecordingDriver.executions);
        assertEquals(0, RecordingDriver.durableWrites);
        DBFactory.shutdown();
        assertTrue(RecordingDriver.connections.stream().allMatch(c -> c.closed));
    }

    private static HikariDataSource pool() throws Exception {
        Field field = DBFactory.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        return (HikariDataSource) field.get(null);
    }

    private static void set(String name, Object value) throws Exception {
        Field field = DBFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
