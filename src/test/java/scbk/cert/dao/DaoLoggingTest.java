package scbk.cert.dao;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import scbk.cert.util.Log;
import scbk.cert.util.LogCapture;

/** Actual DAO/DBFactory error paths; the in-memory driver never opens a socket. */
@ResourceLock("LOG_CONFIGURATION")
class DaoLoggingTest {
    @BeforeEach void configure() throws Exception {
        resetFactory();
        RecordingDriver.reset();
        RecordingDriver.legacyCommit = false;
        LegacyJdbcBaseline.installConfiguration();
    }

    @AfterEach void close() throws Exception {
        try { resetFactory(); }
        finally { Log.endRequest(); }
    }

    @Test void daoFailureDoesNotPublishSqlOrInputAndRetainsItsReturnValue() throws Exception {
        AtomicBoolean statementClosed = new AtomicBoolean();
        AtomicBoolean connectionClosed = new AtomicBoolean();
        // A JDBC query fails by throwing SQLException, not by returning null.
        // Keep this fixture outside Hikari's result wrapping and exercise the
        // real DAO catch/finally without changing production cleanup behavior.
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {PreparedStatement.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("executeQuery")) {
                        throw new SQLException("SELECT COUNT SYNTHETIC_PRIVATE_DEPARTMENT", "42000", 942);
                    }
                    if (method.getName().equals("close")) {
                        statementClosed.set(true);
                        return null;
                    }
                    throw new AssertionError("Unexpected fixture call: " + method.getName());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("prepareStatement")) return statement;
                    if (method.getName().equals("close")) {
                        connectionClosed.set(true);
                        return null;
                    }
                    throw new AssertionError("Unexpected fixture call: " + method.getName());
                });
        set("dataSource", new HikariDataSource() {
            @Override public Connection getConnection() { return connection; }
        });
        try (LogCapture capture = new LogCapture("log.day", "log.root")) {
            String requestId = Log.beginRequest();
            assertFalse(new KCBDAO("SYNTHETIC_CERT", "SYNTHETIC_INFO")
                    .selectDeptCode("SYNTHETIC_PRIVATE_DEPARTMENT"));
            assertEquals(1, capture.events().size());
            assertEquals(Level.ERROR, capture.events().get(0).getLevel());
            assertNull(capture.events().get(0).getThrown());
            assertTrue(capture.text().contains("event=DB_OPERATION_FAILED operation=KCBDAO.selectDeptCode"));
            assertTrue(capture.text().contains("requestId=" + requestId));
            assertFalse(capture.text().contains("SYNTHETIC_PRIVATE_DEPARTMENT"));
            assertFalse(capture.text().contains("SELECT COUNT"));
            assertFalse(capture.text().contains("WHERE DEP_ID"));
            assertTrue(capture.text().contains("sqlState=42000 vendorCode=942"));
            assertTrue(statementClosed.get());
            assertTrue(connectionClosed.get());
        }
    }

    @Test void poolAcquisitionFailureKeepsNullReturnAndRedactsDriverDetails() throws Exception {
        HikariDataSource failedPool = new HikariDataSource() {
            @Override public Connection getConnection() throws SQLException {
                throw new SQLException("SYNTHETIC_PASSWORD jdbc:PRIVATE_URL PRIVATE_ACCOUNT", "08006", 17002);
            }
        };
        set("dataSource", failedPool);
        try (LogCapture capture = new LogCapture("log.day", "log.root")) {
            assertNull(DBFactory.getInstance().getConnection());
            assertEquals(1, capture.events().size());
            assertEquals(Level.ERROR, capture.events().get(0).getLevel());
            assertNull(capture.events().get(0).getThrown());
            assertTrue(capture.text().contains("event=DB_CONNECTION_ACQUIRE_FAILED"));
            assertTrue(capture.text().contains("sqlState=08006 vendorCode=17002"));
            for (String secret : new String[] {"SYNTHETIC_PASSWORD", "PRIVATE_URL", "PRIVATE_ACCOUNT"}) {
                assertFalse(capture.text().contains(secret));
            }
        }
    }

    private static void resetFactory() throws Exception {
        DBFactory.shutdown();
        set("dataSource", null);
        set("instance", null);
        set("configBean", null);
    }

    private static void set(String name, Object value) throws Exception {
        Field field = DBFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
