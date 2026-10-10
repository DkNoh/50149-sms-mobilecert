package scbk.cert.dao;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/** In-memory JDBC recorder. This driver never opens a socket. */
public final class RecordingDriver implements Driver {
    public static final List<Record> connections = new CopyOnWriteArrayList<Record>();
    public static volatile int failStatement;
    public static volatile boolean unavailable;
    public static volatile boolean legacyCommit = true;
    public static int executions;
    public static int durableWrites;
    public static int commits;
    public static int rollbacks;

    static {
        try { DriverManager.registerDriver(new RecordingDriver()); }
        catch (SQLException e) { throw new ExceptionInInitializerError(e); }
    }

    public static void reset() {
        connections.clear();
        failStatement = 0;
        unavailable = false;
        legacyCommit = true;
        executions = durableWrites = commits = rollbacks = 0;
    }

    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null;
        if (unavailable) throw new SQLException("Synthetic database unavailable");
        Record record = new Record();
        record.compatibleCommit = legacyCommit || "false".equals(
                info.getProperty("oracle.jdbc.autoCommitSpecCompliant"));
        record.properties.putAll(info);
        connections.add(record);
        return proxy(Connection.class, record);
    }

    public boolean acceptsURL(String url) { return url != null && url.startsWith("jdbc:mobilecert-test:"); }
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
    public int getMajorVersion() { return 1; }
    public int getMinorVersion() { return 0; }
    public boolean jdbcCompliant() { return false; }
    public Logger getParentLogger() { return Logger.getLogger("mobilecert.test"); }

    public static final class Record implements InvocationHandler {
        public boolean autoCommit = true;
        public boolean closed;
        public boolean compatibleCommit;
        public int pendingWrites;
        public final Properties properties = new Properties();
        public final List<Boolean> autoCommitChanges = new CopyOnWriteArrayList<Boolean>();
        public final List<StatementRecord> statements = new CopyOnWriteArrayList<StatementRecord>();

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("getAutoCommit")) return autoCommit;
            if (name.equals("setAutoCommit")) {
                boolean next = (Boolean) args[0];
                if (next && !autoCommit) { durableWrites += pendingWrites; pendingWrites = 0; }
                autoCommit = next;
                autoCommitChanges.add(next);
                return null;
            }
            if (name.equals("prepareStatement") || name.equals("createStatement")) {
                StatementRecord statement = new StatementRecord(this);
                statements.add(statement);
                return name.equals("prepareStatement") ? proxy(PreparedStatement.class, statement) : proxy(Statement.class, statement);
            }
            if (name.equals("commit")) {
                if (autoCommit && !compatibleCommit) throw new SQLException("Synthetic strict auto-commit rejection");
                commits++;
                durableWrites += pendingWrites;
                pendingWrites = 0;
                return null;
            }
            if (name.equals("rollback")) { rollbacks++; pendingWrites = 0; return null; }
            if (name.equals("close") || name.equals("abort")) { closed = true; pendingWrites = 0; return null; }
            if (name.equals("isClosed")) return closed;
            if (name.equals("isValid")) return !closed;
            if (name.equals("getTransactionIsolation")) return Connection.TRANSACTION_READ_COMMITTED;
            if (name.equals("unwrap")) return ((Class<?>) args[0]).cast(proxy);
            if (name.equals("isWrapperFor")) return ((Class<?>) args[0]).isInstance(proxy);
            if (name.equals("toString")) return "SyntheticConnection";
            return defaultValue(method.getReturnType());
        }
    }

    public static final class StatementRecord implements InvocationHandler {
        public boolean closed;
        private final Record connection;
        StatementRecord(Record connection) { this.connection = connection; }
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("executeUpdate")) {
                executions++;
                if (executions == failStatement) throw new SQLException("Synthetic statement failure");
                if (connection.autoCommit) durableWrites++; else connection.pendingWrites++;
                return 1;
            }
            if (name.equals("close")) { closed = true; return null; }
            if (name.equals("isClosed")) return closed;
            return defaultValue(method.getReturnType());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(RecordingDriver.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return (char) 0;
        return null;
    }
}
