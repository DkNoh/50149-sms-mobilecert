package scbk.cert.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.time.Duration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HikariMigrationTest {
    @BeforeEach
    void reset() throws Exception {
        DBFactory.shutdown();
        set("dataSource", null);
        set("instance", null);
        set("configBean", null);
        RecordingDriver.reset();
        RecordingDriver.legacyCommit = false;
        LegacyJdbcBaseline.installConfiguration();
        System.clearProperty("scbk.cert.db.connectionTimeoutMs");
    }

    @AfterEach
    void stop() {
        DBFactory.shutdown();
        System.clearProperty("scbk.cert.db.connectionTimeoutMs");
    }

    @Test
    void preservesLegacyDaoOutcomesWithStrictDriverAndClosesStatements() throws Exception {
        LegacyJdbcBaseline.verify();
        assertTrue(RecordingDriver.connections.stream().allMatch(c ->
                "false".equals(c.properties.getProperty("oracle.jdbc.autoCommitSpecCompliant"))));
        assertTrue(RecordingDriver.connections.stream().flatMap(c -> c.statements.stream()).allMatch(s -> s.closed));
        assertTrue(DBFactory.getInstance().dumpPoolInfo().contains("active=0"));
    }

    @Test
    void poolIsLazyReusesReturnedConnectionsAndClosesPhysicalConnections() throws Exception {
        DBFactory db = DBFactory.getInstance();
        assertEquals(0, RecordingDriver.connections.size());
        Connection first = db.getConnection();
        assertNotNull(first);
        assertTrue(first.getAutoCommit());
        db.close(first);
        int created = RecordingDriver.connections.size();
        try (Connection second = db.getConnection()) {
            assertNotNull(second);
            assertEquals(created, RecordingDriver.connections.size());
        }
        DBFactory.shutdown();
        assertTrue(RecordingDriver.connections.stream().allMatch(c -> c.closed));
        assertNull(db.getConnection());
        assertEquals(created, RecordingDriver.connections.size());
    }

    @Test
    void databaseUnavailableStillReturnsNullAndCanRecover() {
        DBFactory db = DBFactory.getInstance();
        RecordingDriver.unavailable = true;
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertNull(db.getConnection()));
        RecordingDriver.unavailable = false;
        Connection connection = db.getConnection();
        assertNotNull(connection);
        db.close(connection);
    }

    @Test
    void exhaustedPoolReturnsNullAtConfiguredAcquisitionTimeout() throws Exception {
        System.setProperty("scbk.cert.db.connectionTimeoutMs", "250");
        DBFactory db = DBFactory.getInstance();
        try (Connection first = db.getConnection(); Connection second = db.getConnection()) {
            assertNotNull(first);
            assertNotNull(second);
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertNull(db.getConnection()));
        }
        try (Connection recovered = db.getConnection()) { assertNotNull(recovered); }
    }

    @Test
    void shutdownBeforeFirstUseDoesNotInitializeFactoryOrReadConfiguration() throws Exception {
        set("configBean", null);
        DBFactory.shutdown();
        assertNull(get("instance"));
        assertNull(get("dataSource"));
        assertNull(get("configBean"));
        assertEquals(0, RecordingDriver.connections.size());
    }

    @Test
    void failedManualTransactionIsCleanedBeforePoolReuse() throws Exception {
        DBFactory db = DBFactory.getInstance();
        try (Connection connection = db.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement statement = connection.prepareStatement("UPDATE SYNTHETIC SET VALUE=1")) {
                statement.executeUpdate();
            }
            // Simulate a caller abandoning an uncommitted transaction.
        }
        assertEquals(1, RecordingDriver.rollbacks);
        assertEquals(0, RecordingDriver.durableWrites);
        try (Connection next = db.getConnection()) { assertTrue(next.getAutoCommit()); }
    }

    @Test
    void mapsExistingConfigurationWithoutConvertingAutoCommitOrTimeoutUnits() {
        try (HikariDataSource pool = DBFactory.createDataSource(LegacyJdbcBaseline.configuration())) {
            assertEquals(2, pool.getMaximumPoolSize());
            assertEquals(0, pool.getMinimumIdle());
            assertEquals(60000, pool.getIdleTimeout());
            assertEquals(60000, pool.getConnectionTimeout());
            assertEquals(60000, pool.getLeakDetectionThreshold());
            assertEquals(0, pool.getMaxLifetime());
            assertEquals(0, pool.getKeepaliveTime());
            assertTrue(pool.isAutoCommit());
            assertNull(pool.getHikariPoolMXBean());
        }
    }

    private static Object get(String name) throws Exception {
        Field field = DBFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void set(String name, Object value) throws Exception {
        Field field = DBFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
