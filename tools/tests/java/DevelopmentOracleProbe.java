import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only, existing-schema Oracle development connectivity probe.
 * Supply nine UTF-8 strings on stdin, each preceded by a four-byte big-endian
 * length, then close stdin. No credentials or configuration are command arguments.
 * Only the launcher may decide whether a development endpoint is authorized.
 * This class never creates objects, reads application rows, or advances a sequence.
 */
public final class DevelopmentOracleProbe {
    private static final int FIELD_COUNT = 9;
    private static final int MAX_FIELD_BYTES = 4096;
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final Pattern URL = Pattern.compile(
            "jdbc:oracle:thin:@//([A-Za-z0-9.-]{1,253}):([0-9]{1,5})/([A-Za-z0-9][A-Za-z0-9_.-]{0,127})");
    private static final Pattern HOST_LABEL = Pattern.compile(
            "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private static final Pattern TABLE = Pattern.compile("[A-Z][A-Z0-9_]{0,29}");
    private static final Pattern CHARSET = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
    private static final Pattern SQL_STATE = Pattern.compile("[A-Z0-9]{5}");
    private static final String CERT_COLUMNS =
            "TRAN_DT, TRAN_SERNO, TRAN_TM, DEPT_CD, CERT_PRGRSS_PHASE_MK, "
            + "TREATE_MK, CLERK_NO, TRAN_NO, MBL_COMM_MK, MBLPHN_NO, HLDR_SSN, "
            + "CERT_NO_SMS_SND_FLG, CERT_NO, CALLBACK_NO, MRCHNT_NM, REAL_NM_CNFRM_IND, "
            + "NM, CERT_TRAN_SERNO, CERT_TRAN_DTM, CERT_MODE, TRAN_RSPNS_CD, "
            + "TRAN_RSPNS_MSG, TRAN_SVC_RSPNS_CD, TRAN_SVC_RSPNS_MSG";

    private DevelopmentOracleProbe() { }

    public static void main(String[] args) {
        PrintStream resultOutput = System.out;
        PrintStream discarded = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        System.setOut(discarded);
        System.setErr(discarded);
        Outcome outcome = new Outcome();
        int exitCode;
        try {
            if (args.length != 0) throw new InputFailure("ARGUMENTS_NOT_ALLOWED");
            if (Runtime.version().feature() != 21) throw new InputFailure("JDK_21_REQUIRED");
            Configuration configuration = readConfiguration();
            // Suppress JDBC/JUL diagnostics before loading any driver class.
            LogManager.getLogManager().reset();
            java.util.logging.Logger.getLogger("").setLevel(Level.OFF);
            java.util.logging.Logger.getLogger("oracle").setLevel(Level.OFF);
            java.util.logging.Logger.getLogger("oracle.jdbc").setLevel(Level.OFF);
            DriverManager.setLogWriter(null);
            DriverManager.setLoginTimeout(5);
            try {
                Class.forName("oracle.jdbc.OracleDriver");
            } catch (ClassNotFoundException | LinkageError unavailable) {
                throw new InputFailure("ORACLE_DRIVER_UNAVAILABLE");
            }
            probe(configuration, outcome);
            exitCode = outcome.failureCode == null ? 0 : 1;
        } catch (InputFailure invalid) {
            outcome.fail(invalid.code, null);
            exitCode = 2;
        } catch (Throwable unexpected) {
            // Never render exception text, arguments, SQL, connection details, or a stack trace.
            outcome.fail("PROBE_ENVIRONMENT_FAILURE", null);
            exitCode = 2;
        }
        resultOutput.println(outcome.json());
        resultOutput.flush();
        System.exit(exitCode);
    }

    private static Configuration readConfiguration() throws InputFailure {
        String[] fields = new String[FIELD_COUNT];
        try {
            DataInputStream input = new DataInputStream(System.in);
            for (int index = 0; index < FIELD_COUNT; index++) {
                int length = input.readInt();
                if (length < 1 || length > MAX_FIELD_BYTES) throw new InputFailure("INVALID_INPUT_LENGTH");
                byte[] bytes = new byte[length];
                input.readFully(bytes);
                fields[index] = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
                java.util.Arrays.fill(bytes, (byte) 0);
                if (fields[index].isBlank() || fields[index].codePoints().anyMatch(Character::isISOControl)) {
                    throw new InputFailure("INVALID_INPUT_FIELD");
                }
            }
            if (input.read() != -1) throw new InputFailure("EXTRA_INPUT");
        } catch (EOFException truncated) {
            throw new InputFailure("TRUNCATED_INPUT");
        } catch (CharacterCodingException malformed) {
            throw new InputFailure("INVALID_UTF8_INPUT");
        } catch (IOException unavailable) {
            throw new InputFailure("INPUT_READ_FAILURE");
        }

        Matcher url = URL.matcher(fields[0]);
        if (!url.matches()) throw new InputFailure("INVALID_JDBC_URL");
        for (String label : url.group(1).split("\\.", -1)) {
            if (!HOST_LABEL.matcher(label).matches()) throw new InputFailure("INVALID_JDBC_URL");
        }
        int port = Integer.parseInt(url.group(2));
        if (port < 1 || port > 65535) throw new InputFailure("INVALID_JDBC_URL");
        if (!TABLE.matcher(fields[6]).matches() || !TABLE.matcher(fields[7]).matches()) {
            throw new InputFailure("INVALID_TABLE_IDENTIFIER");
        }
        if (!fields[8].matches("[1-9][0-9]{0,2}")) throw new InputFailure("INVALID_EXPECTED_DB_VERSION");
        return new Configuration(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5],
                fields[6], fields[7], Integer.parseInt(fields[8]));
    }

    private static void probe(Configuration configuration, Outcome outcome) {
        Connection connection = null;
        String stage = "CONNECTION_FAILED";
        try {
            Properties properties = new Properties();
            properties.setProperty("user", configuration.user);
            properties.setProperty("password", configuration.password);
            properties.setProperty("oracle.net.encryption_client", "REQUIRED");
            properties.setProperty("oracle.net.crypto_checksum_client", "REQUIRED");
            properties.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
            properties.setProperty("oracle.jdbc.ReadTimeout", "10000");
            try {
                connection = DriverManager.getConnection(configuration.url, properties);
            } finally {
                properties.clear();
            }
            outcome.checks.add("connectionWithRequiredEncryptionAndIntegrity");

            stage = "READ_ONLY_TRANSACTION_FAILED";
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.execute("SET TRANSACTION READ ONLY");
            }
            outcome.checks.add("readOnlyTransaction");

            stage = "DB_IDENTITY_CHECK_FAILED";
            verifyIdentity(connection, configuration);
            outcome.checks.add("databaseIdentity");

            stage = "DB_VERSION_CHECK_FAILED";
            DatabaseMetaData metadata = connection.getMetaData();
            outcome.dbMajorVersion = metadata.getDatabaseMajorVersion();
            outcome.driverMajorVersion = metadata.getDriverMajorVersion();
            if (outcome.dbMajorVersion != configuration.expectedMajorVersion) {
                throw new CheckFailure("DB_VERSION_MISMATCH");
            }
            outcome.checks.add("databaseMajorVersion");

            stage = "CERT_COLUMNS_CHECK_FAILED";
            verifyColumns(connection, "SMS." + configuration.certTable, CERT_COLUMNS);
            outcome.checks.add("certificateColumns");
            stage = "INFO_COLUMNS_CHECK_FAILED";
            verifyColumns(connection, "SMS." + configuration.infoTable, "TRAN_DT, TRAN_SERNO, ETC1, ETC2");
            outcome.checks.add("certificateInfoColumns");
            stage = "DEPT_COLUMNS_CHECK_FAILED";
            verifyColumns(connection, "SMS.DEP", "DEP_ID, ACT_YN");
            outcome.checks.add("departmentColumns");

            stage = "SEQUENCE_METADATA_CHECK_FAILED";
            try (PreparedStatement statement = query(connection,
                    "SELECT COUNT(*) FROM ALL_SEQUENCES WHERE SEQUENCE_OWNER = ? AND SEQUENCE_NAME = ?")) {
                statement.setString(1, "SMS");
                statement.setString(2, "MBLPHN_SELF_CERT_SEQ");
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != 1) throw new CheckFailure("SEQUENCE_METADATA_NOT_VISIBLE");
                }
            }
            outcome.checks.add("sequenceMetadataOnly");

            stage = "CHARSET_CHECK_FAILED";
            try (PreparedStatement statement = query(connection,
                    "SELECT PARAMETER, VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER IN (?, ?)")) {
                statement.setString(1, "NLS_CHARACTERSET");
                statement.setString(2, "NLS_NCHAR_CHARACTERSET");
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String parameter = rows.getString(1);
                        String value = rows.getString(2);
                        if (value == null || !CHARSET.matcher(value).matches()) {
                            throw new CheckFailure("INVALID_CHARSET_METADATA");
                        }
                        if ("NLS_CHARACTERSET".equals(parameter) && outcome.databaseCharset == null) {
                            outcome.databaseCharset = value;
                        } else if ("NLS_NCHAR_CHARACTERSET".equals(parameter) && outcome.nationalCharset == null) {
                            outcome.nationalCharset = value;
                        } else {
                            throw new CheckFailure("INVALID_CHARSET_METADATA");
                        }
                    }
                }
            }
            if (outcome.databaseCharset == null || outcome.nationalCharset == null) {
                throw new CheckFailure("CHARSET_METADATA_INCOMPLETE");
            }
            outcome.checks.add("databaseCharsets");
        } catch (CheckFailure mismatch) {
            outcome.fail(mismatch.code, null);
        } catch (SQLException failure) {
            outcome.fail(stage, failure);
        } catch (RuntimeException failure) {
            outcome.fail("DB_PROBE_RUNTIME_FAILURE", null);
        } finally {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException failure) {
                    outcome.fail("ROLLBACK_FAILED", failure);
                } catch (RuntimeException failure) {
                    outcome.fail("ROLLBACK_FAILED", null);
                } finally {
                    try {
                        connection.close();
                    } catch (SQLException failure) {
                        outcome.fail("CONNECTION_CLOSE_FAILED", failure);
                    } catch (RuntimeException failure) {
                        outcome.fail("CONNECTION_CLOSE_FAILED", null);
                    }
                }
            }
        }
    }

    private static void verifyIdentity(Connection connection, Configuration configuration)
            throws SQLException, CheckFailure {
        String sql = "SELECT CASE WHEN UPPER(SYS_CONTEXT('USERENV','DB_UNIQUE_NAME')) = UPPER(?) THEN 1 ELSE 0 END, "
                + "CASE WHEN UPPER(SYS_CONTEXT('USERENV','SERVICE_NAME')) = UPPER(?) THEN 1 ELSE 0 END, "
                + "CASE WHEN UPPER(SYS_CONTEXT('USERENV','CON_NAME')) = UPPER(?) THEN 1 ELSE 0 END FROM DUAL";
        try (PreparedStatement statement = query(connection, sql)) {
            statement.setString(1, configuration.expectedDbUniqueName);
            statement.setString(2, configuration.expectedServiceName);
            statement.setString(3, configuration.expectedConName);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1 || rows.getInt(2) != 1 || rows.getInt(3) != 1) {
                    throw new CheckFailure("DB_IDENTITY_MISMATCH");
                }
            }
        }
    }

    private static void verifyColumns(Connection connection, String table, String columns)
            throws SQLException, CheckFailure {
        // table is built solely from the fixed SMS owner and a validated identifier.
        try (PreparedStatement statement = query(connection, "SELECT " + columns + " FROM " + table + " WHERE 1=0");
             ResultSet rows = statement.executeQuery()) {
            if (rows.next()) throw new CheckFailure("UNEXPECTED_APPLICATION_ROW");
        }
    }

    private static PreparedStatement query(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            return statement;
        } catch (SQLException | RuntimeException failure) {
            try {
                statement.close();
            } catch (SQLException ignored) {
                // The original, sanitized failure identifies this check.
            }
            throw failure;
        }
    }

    private record Configuration(String url, String user, String password, String expectedDbUniqueName,
            String expectedServiceName, String expectedConName, String certTable, String infoTable,
            int expectedMajorVersion) { }

    private static final class Outcome {
        private final List<String> checks = new ArrayList<>();
        private String failureCode;
        private String sqlState;
        private Integer errorCode;
        private Integer dbMajorVersion;
        private Integer driverMajorVersion;
        private String databaseCharset;
        private String nationalCharset;

        void fail(String code, SQLException failure) {
            if (failureCode != null) return;
            failureCode = code;
            if (failure != null) {
                String state = failure.getSQLState();
                sqlState = state != null && SQL_STATE.matcher(state).matches() ? state : null;
                errorCode = failure.getErrorCode();
            }
        }

        String json() {
            StringBuilder json = new StringBuilder("{\"status\":\"");
            if (failureCode == null) {
                json.append("PASS\",\"checks\":[");
                for (int index = 0; index < checks.size(); index++) {
                    if (index != 0) json.append(',');
                    json.append('"').append(checks.get(index)).append('"');
                }
                json.append(']');
            } else {
                json.append("FAIL\",\"failureCode\":\"").append(failureCode).append('"');
                json.append(",\"sqlState\":");
                if (sqlState == null) json.append("\"UNKNOWN\"");
                else json.append('"').append(sqlState).append('"');
                json.append(",\"errorCode\":").append(errorCode == null ? 0 : errorCode);
            }
            if (dbMajorVersion != null) json.append(",\"dbMajorVersion\":").append(dbMajorVersion);
            if (driverMajorVersion != null) json.append(",\"driverMajorVersion\":").append(driverMajorVersion);
            if (databaseCharset != null) json.append(",\"charset\":\"").append(databaseCharset).append('"');
            if (nationalCharset != null) json.append(",\"ncharset\":\"").append(nationalCharset).append('"');
            return json.append('}').toString();
        }
    }

    private static final class InputFailure extends Exception {
        private static final long serialVersionUID = 1L;
        final String code;
        InputFailure(String code) { this.code = code; }
    }

    private static final class CheckFailure extends Exception {
        private static final long serialVersionUID = 1L;
        final String code;
        CheckFailure(String code) { this.code = code; }
    }
}
