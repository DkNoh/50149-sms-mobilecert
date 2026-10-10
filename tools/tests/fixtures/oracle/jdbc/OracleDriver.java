/*
 * FAKE JDBC DRIVER FOR OFFLINE TESTS ONLY. THIS IS NOT THE ORACLE JDBC DRIVER.
 * No socket, real database, or external service is used by this fixture.
 * Compile only into the temporary directory made by test_development_probe.py.
 * NEVER copy this fixture or its classes into target/lib, an application JAR,
 * or any development/production application's classpath.
 */
package oracle.jdbc;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public final class OracleDriver implements Driver {
    private static final String MODE = System.getenv("PROBE_STUB_MODE");
    private static boolean readOnly;

    static {
        try {
            DriverManager.registerDriver(new OracleDriver());
        } catch (SQLException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void audit(String event) {
        try {
            Files.writeString(Path.of(System.getenv("PROBE_STUB_AUDIT")), event + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new AssertionError("STUB_SECRET_AUDIT");
        }
    }

    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("STUB_SECRET_ASSERTION");
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(OracleDriver.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static AssertionError unexpectedMethod() {
        return new AssertionError("STUB_SECRET_UNEXPECTED_JDBC_METHOD");
    }

    @Override
    public Connection connect(String url, Properties properties) throws SQLException {
        require(acceptsURL(url));
        require("REQUIRED".equals(properties.getProperty("oracle.net.encryption_client")));
        require("REQUIRED".equals(properties.getProperty("oracle.net.crypto_checksum_client")));
        require("5000".equals(properties.getProperty("oracle.net.CONNECT_TIMEOUT")));
        require("10000".equals(properties.getProperty("oracle.jdbc.ReadTimeout")));
        audit("connection_properties_required");
        // Synthetic sentinels must never escape the probe's output suppression.
        System.out.println("STUB_SECRET_STDOUT");
        System.err.println("STUB_SECRET_STDERR");
        if ("connection_failure".equals(MODE)) {
            throw new SQLException("STUB_SECRET_CONNECTION", "08006", 12514);
        }
        return (Connection) proxy(Connection.class, (object, method, args) -> {
            switch (method.getName()) {
                case "setAutoCommit":
                    require(Boolean.FALSE.equals(args[0]));
                    audit("auto_commit_false");
                    return null;
                case "createStatement":
                    require(!readOnly);
                    return statement(null);
                case "prepareStatement":
                    require(readOnly);
                    return statement((String) args[0]);
                case "getMetaData":
                    require(readOnly);
                    return proxy(DatabaseMetaData.class, (target, metadataMethod, values) ->
                            switch (metadataMethod.getName()) {
                                case "getDatabaseMajorVersion" -> "version_mismatch".equals(MODE) ? 23 : 19;
                                case "getDriverMajorVersion" -> 23;
                                default -> throw unexpectedMethod();
                            });
                case "rollback":
                    audit("rollback");
                    if ("rollback_failure".equals(MODE)) {
                        throw new SQLException("STUB_SECRET_ROLLBACK", "08006", 17002);
                    }
                    return null;
                case "close":
                    audit("connection_close");
                    if ("close_failure".equals(MODE)) {
                        throw new SQLException("STUB_SECRET_CLOSE", "08006", 17002);
                    }
                    return null;
                default:
                    // Includes commit, prepareCall and every unapproved operation.
                    throw unexpectedMethod();
            }
        });
    }

    private static Object statement(String sql) {
        if (sql != null) {
            require(sql.startsWith("SELECT "));
            require(!sql.matches("(?s).*\\b(INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE|"
                    + "GRANT|REVOKE|NEXTVAL|CURRVAL)\\b.*"));
        }
        return proxy(sql == null ? Statement.class : PreparedStatement.class, new InvocationHandler() {
            private boolean timeout;
            private final Map<Integer, String> binds = new HashMap<>();

            @Override
            public Object invoke(Object object, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "setQueryTimeout":
                        require(((Integer) args[0]) == 5);
                        timeout = true;
                        return null;
                    case "setString":
                        binds.put((Integer) args[0], (String) args[1]);
                        return null;
                    case "execute":
                        require(sql == null && timeout && "SET TRANSACTION READ ONLY".equals(args[0]) && !readOnly);
                        audit("set_transaction_read_only_first");
                        if ("readonly_failure".equals(MODE)) {
                            throw new SQLException("STUB_SECRET_READONLY", "25000", 1453);
                        }
                        readOnly = true;
                        return true;
                    case "executeQuery":
                        require(timeout && readOnly);
                        if (sql.contains("SYS_CONTEXT")) {
                            require(binds.size() == 3);
                            audit("identity_bound_check");
                            return rows(new Object[][] {{"identity_mismatch".equals(MODE) ? 0 : 1, 1, 1}});
                        }
                        if (sql.contains("ALL_SEQUENCES")) {
                            require(binds.size() == 2);
                            audit("sequence_metadata_only");
                            return rows(new Object[][] {{"sequence_missing".equals(MODE) ? 0 : 1}});
                        }
                        if (sql.contains("NLS_DATABASE_PARAMETERS")) {
                            require(binds.size() == 2);
                            audit("charset_metadata");
                            return rows(new Object[][] {
                                    {"NLS_CHARACTERSET", "invalid_charset".equals(MODE)
                                            ? "STUB_SECRET_BAD/CHARSET" : "AL32UTF8"},
                                    {"NLS_NCHAR_CHARACTERSET", "AL16UTF16"}});
                        }
                        require(sql.endsWith(" WHERE 1=0") && sql.contains(" FROM SMS.")
                                && !sql.startsWith("SELECT *"));
                        audit("explicit_columns_no_rows");
                        if ("columns_failure".equals(MODE)) {
                            throw new SQLException("STUB_SECRET_SQL", "42000", 942);
                        }
                        return rows(new Object[0][]);
                    case "close":
                        audit("statement_close");
                        return null;
                    default:
                        // Includes update/batch methods and unapproved SQL execution.
                        throw unexpectedMethod();
                }
            }
        });
    }

    private static ResultSet rows(Object[][] values) {
        return (ResultSet) proxy(ResultSet.class, new InvocationHandler() {
            private int index = -1;

            @Override
            public Object invoke(Object object, Method method, Object[] args) {
                return switch (method.getName()) {
                    case "next" -> ++index < values.length;
                    case "getInt", "getString" -> values[index][(Integer) args[0] - 1];
                    case "close" -> {
                        audit("result_set_close");
                        yield null;
                    }
                    default -> throw unexpectedMethod();
                };
            }
        });
    }

    @Override
    public boolean acceptsURL(String url) { return url.startsWith("jdbc:oracle:thin:"); }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() { return 23; }

    @Override
    public int getMinorVersion() { return 0; }

    @Override
    public boolean jdbcCompliant() { return true; }

    @Override
    public Logger getParentLogger() { return Logger.getLogger("oracle.jdbc"); }
}
