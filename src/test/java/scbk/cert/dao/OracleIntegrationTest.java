package scbk.cert.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.conf.DBConfigBean;

/**
 * Real ojdbc11/Hikari/DAO tests, with synthetic data in a disposable local PDB.
 * No SQL rewriting or mock driver: the legacy DAO's SMS namespace is exercised.
 * The schema and marker must already be provisioned by the Oracle test runner.
 */
@Tag("oracle")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@Execution(ExecutionMode.SAME_THREAD)
class OracleIntegrationTest {
    private static final String TIMEOUT_PROPERTY = "scbk.cert.db.connectionTimeoutMs";
    private static final Pattern LOCAL_TEST_URL = Pattern.compile(
            "^jdbc:oracle:thin:@//127\\.0\\.0\\.1:([0-9]{1,5})/(MCERTIT[A-F0-9]{8})$",
            Pattern.CASE_INSENSITIVE);
    private static final String KOREAN_NAME = "\uac00\uc0c1\uac80\uc99d\uc0ac\uc6a9\uc790";
    private static final String KOREAN_MERCHANT = "\ubcf8\uc778\uc778\uc99d\uc2dc\ud5d8\uc0c1\uc810";
    private static final String KOREAN_RESPONSE = "\ubcf8\uc778\uc778\uc99d \uc815\uc0c1\uc751\ub2f5";
    private static final String FAIL_SECOND_WRITE = "SYNTHETIC_FORCE_FAILURE";

    private String previousTimeout;
    private String url;
    private String user;
    private String password;
    private String pdb;
    private String serial;
    private String currentDtm;
    private boolean safeSchema;
    private DBFactory db;
    private HikariDataSource pool;
    private final KCBDAO dao = new KCBDAO("KCB_CERT", "KCB_INFO");

    @BeforeEach
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void configureGuardedRealOraclePool() throws Exception {
        previousTimeout = System.getProperty(TIMEOUT_PROPERTY);
        url = requiredEnvironment("MOBILECERT_ORACLE_URL");
        user = requiredEnvironment("MOBILECERT_ORACLE_USER");
        password = requiredEnvironment("MOBILECERT_ORACLE_PASSWORD");
        pdb = requiredEnvironment("MOBILECERT_ORACLE_PDB");
        Matcher match = LOCAL_TEST_URL.matcher(url);
        assertTrue(match.matches(), "Oracle integration URL must use loopback and a disposable MCERTIT PDB");
        int port = Integer.parseInt(match.group(1));
        assertTrue(port > 0 && port <= 65535, "Invalid local Oracle test port");
        assertTrue(pdb.matches("(?i)MCERTIT[A-F0-9]{8}"), "Disposable test PDB name required");
        assertTrue(pdb.equalsIgnoreCase(match.group(2)), "URL service must match the disposable test PDB");
        assertEquals("SMS", user, "Only the synthetic SMS owner in the disposable PDB is supported");

        resetFactory();
        System.setProperty(TIMEOUT_PROPERTY, "350");
        DBConfigBean bean = new DBConfigBean();
        bean.setJdbcDriver("oracle.jdbc.OracleDriver");
        bean.setDbUrl(url);
        bean.setDbUser(user);
        bean.setDbPassword(password);
        bean.setConnection("1");
        bean.setPoolName("SyntheticOracleIntegrationPool");
        setFactory("configBean", bean);
        db = DBFactory.getInstance();
        pool = (HikariDataSource) getFactory("dataSource");
        assertNotNull(pool, "The actual DBFactory must create a Hikari pool");
        // Test-only bounds: production configuration and SQL remain unchanged.
        pool.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "3000");
        pool.addDataSourceProperty("oracle.jdbc.ReadTimeout", "5000");
        pool.setValidationTimeout(1000);

        try (Connection connection = borrowed()) {
            assertSafeDatabase(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT MARKER_VALUE FROM MCERT_IT_MARKER WHERE MARKER_KEY = 'SYNTHETIC_SCHEMA'" );
                 ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "Synthetic schema marker must exist before any writes");
                assertEquals("MOBILECERT_ORACLE_IT_V1", rows.getString(1));
            }
            safeSchema = true;
            serial = scalar(connection, "SELECT SMS.MBLPHN_SELF_CERT_SEQ.NEXTVAL FROM DUAL");
            currentDtm = scalar(connection, "SELECT TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS') FROM DUAL");
        }
    }

    @AfterEach
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void removeOnlyThisTestsSyntheticRowsAndClosePool() throws Exception {
        try {
            if (safeSchema && serial != null) {
                try (Connection connection = freshConnection()) {
                    assertSafeDatabase(connection);
                    deleteRow(connection, "DELETE FROM SMS.KCB_INFO WHERE TRAN_SERNO = ?");
                    deleteRow(connection, "DELETE FROM SMS.KCB_CERT WHERE TRAN_SERNO = ?");
                }
            }
            if (pool != null && pool.getHikariPoolMXBean() != null) {
                assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections(),
                        "Each DAO path must return its borrowed connection");
            }
        } finally {
            resetFactory();
            if (previousTimeout == null) System.clearProperty(TIMEOUT_PROPERTY);
            else System.setProperty(TIMEOUT_PROPERTY, previousTimeout);
        }
    }

    @Test
    void usesSupportedJdkActualOracleDriverAndHikariConnection() throws Exception {
        assertTrue(java.util.Arrays.asList("1.8", "21").contains(System.getProperty("java.specification.version")),
                "Run validation with the JDK selected by the Maven profile");
        try (Connection connection = borrowed()) {
            assertEquals("Oracle", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getMetaData().getDriverName().contains("Oracle"));
            assertTrue(connection.getMetaData().getDriverMajorVersion() >= 23,
                    "The selected Oracle JDBC dependency must be used");
            assertTrue(connection.getClass().getName().contains("Hikari"));
        }
    }

    @Test
    void legacyAutoCommitAllowsExplicitCommitWithRealDriver() throws Exception {
        try (Connection connection = borrowed()) {
            assertTrue(connection.getAutoCommit(), "Legacy DAO expects auto-commit true");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO SMS.KCB_INFO (TRAN_DT, TRAN_SERNO, ETC1, ETC2) VALUES (?, ?, ?, ?)")) {
                statement.setString(1, currentDtm.substring(0, 8));
                statement.setInt(2, Integer.parseInt(serial));
                statement.setString(3, "SYNTHETIC_COMMIT_CHECK");
                statement.setString(4, "SYNTHETIC");
                assertEquals(1, statement.executeUpdate());
            }
            assertDoesNotThrow(connection::commit,
                    "oracle.jdbc.autoCommitSpecCompliant=false must preserve legacy explicit commits");
        }
        assertEquals("1", scalarFresh("SELECT COUNT(*) FROM SMS.KCB_INFO WHERE TRAN_SERNO = " + serial));
    }

    @Test
    void actualDaoSequenceGeneratesDistinctIncreasingNumbers() {
        String first = dao.getSerno();
        String second = dao.getSerno();
        assertTrue(first.matches("[0-9]+"), "Sequence query must return a number");
        assertTrue(second.matches("[0-9]+"), "Repeated sequence query must return a number");
        assertTrue(Long.parseLong(second) > Long.parseLong(first));
    }

    @Test
    void actualDaoInsertPersistsKoreanFieldsVisibleOutsidePool() throws Exception {
        assertTrue(dao.insert(serial, "TEST_ACTIVE", "SYNTHETIC", currentDtm, request()));
        try (Connection connection = freshConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT NM, MRCHNT_NM, TRAN_NO, CERT_PRGRSS_PHASE_MK, TREATE_MK "
                             + "FROM SMS.KCB_CERT WHERE TRAN_SERNO = ?")) {
            statement.setInt(1, Integer.parseInt(serial));
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "DAO insert must be committed for a separate physical session");
                assertEquals(KOREAN_NAME, rows.getString(1));
                assertEquals(KOREAN_MERCHANT, rows.getString(2));
                assertEquals(request().getTid(), rows.getString(3));
                assertEquals("STEP1", rows.getString(4));
                assertEquals("1", rows.getString(5));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void duplicatePrimaryKeyInsertReportsFailurePreservesOriginalAndReturnsConnection() throws Exception {
        seedCertificate();
        KCBCertReqBean duplicate = request();
        duplicate.setBuyer("DUPLICATE");
        assertFalse(dao.insert(serial, "TEST_ACTIVE", "SYNTHETIC", currentDtm, duplicate),
                "Oracle primary-key violation must not be reported as a successful insert");
        assertEquals("1", scalarFresh("SELECT COUNT(*) FROM SMS.KCB_CERT WHERE TRAN_SERNO = " + serial),
                "Failed duplicate insert must leave exactly the original row");
        assertEquals(KOREAN_NAME, scalarFresh("SELECT NM FROM SMS.KCB_CERT WHERE TRAN_SERNO = " + serial),
                "Failed duplicate insert must not alter the original values");
        assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections(),
                "DAO must return the connection on Oracle constraint failure");
        try (Connection recovered = borrowed()) {
            assertEquals("1", scalar(recovered, "SELECT 1 FROM DUAL"));
        }
    }

    @Test
    void actualDaoSelectCheckReturnsCountTimestampAndDatabaseElapsedMinutes() throws Exception {
        assertTrue(dao.insert(serial, "TEST_ACTIVE", "SYNTHETIC", currentDtm, request()));
        assertEquals(1, db.preparedExecuteUpdate("UPDATE SMS.KCB_CERT SET "
                + "CERT_PRGRSS_PHASE_MK = 'STEP2', TREATE_MK = 'S' WHERE TRAN_SERNO = " + serial));
        String[] result = dao.selectCheck(request());
        assertEquals("1", result[0]);
        assertEquals(currentDtm, result[1]);
        assertNotNull(result[2]);
        assertTrue(Math.abs(Integer.parseInt(result[2])) <= 1,
                "Oracle date arithmetic must use the freshly seeded database time");
    }

    @Test
    void actualDaoDepartmentLookupHonorsActiveFlag() {
        assertTrue(dao.selectDeptCode("TEST_ACTIVE"));
        assertFalse(dao.selectDeptCode("TEST_INACTIVE"));
        assertFalse(dao.selectDeptCode("TEST_MISSING"));
    }

    @Test
    @Tag("readiness")
    void readinessDepartmentLookupMustTreatQuotePredicateAsLiteralInput() {
        assertFalse(dao.selectDeptCode("TEST_MISSING"), "Synthetic department must be absent");
        assertFalse(dao.selectDeptCode("TEST_MISSING' OR '1'='1"),
                "Unknown department containing SQL syntax must not match synthetic active rows");
    }

    @Test
    void actualDaoUpdateStep1CommitsBothRecordsAndKoreanResponse() throws Exception {
        seedCertificate();
        assertTrue(dao.updateStep1(serial, currentDtm, header(), response("SYNTHETIC_DI")));
        assertEquals("9", certificatePhase());
        assertEquals("0000", scalarFresh("SELECT TRAN_RSPNS_CD FROM SMS.KCB_CERT WHERE TRAN_SERNO = " + serial));
        assertEquals(header().getKcbTranSerno() + "|" + KOREAN_RESPONSE,
                scalarFresh("SELECT TRAN_SVC_RSPNS_MSG FROM SMS.KCB_CERT WHERE TRAN_SERNO = " + serial));
        assertEquals("SYNTHETIC_CI", scalarFresh("SELECT ETC1 FROM SMS.KCB_INFO WHERE TRAN_SERNO = " + serial));
        assertEquals("SYNTHETIC_DI", scalarFresh("SELECT ETC2 FROM SMS.KCB_INFO WHERE TRAN_SERNO = " + serial));
    }

    @Test
    void bothDbFactoryUpdateHelpersPreserveRealDriverCommitCompatibility() throws Exception {
        seedCertificate();
        assertEquals(1, db.preparedExecuteUpdate(
                "UPDATE SMS.KCB_CERT SET TREATE_MK = 'P' WHERE TRAN_SERNO = " + serial));
        assertEquals("P", certificatePhase());
        assertEquals(1, db.statementExecuteUpdate(
                "UPDATE SMS.KCB_CERT SET TREATE_MK = 'T' WHERE TRAN_SERNO = " + serial));
        assertEquals("T", certificatePhase());
    }

    @Test
    void closingUncommittedConnectionRollsBackAndResetsAutoCommitBeforeReuse() throws Exception {
        seedCertificate();
        String initialSession;
        try (Connection connection = borrowed()) {
            initialSession = scalar(connection, "SELECT SYS_CONTEXT('USERENV', 'SID') FROM DUAL");
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE SMS.KCB_CERT SET TREATE_MK = 'X' WHERE TRAN_SERNO = ?")) {
                statement.setInt(1, Integer.parseInt(serial));
                assertEquals(1, statement.executeUpdate());
            }
            // Deliberately return without commit/rollback, as a failed caller might.
        }
        try (Connection reused = borrowed()) {
            assertEquals(initialSession, scalar(reused, "SELECT SYS_CONTEXT('USERENV', 'SID') FROM DUAL"),
                    "A size-one pool must exercise the same physical session");
            assertTrue(reused.getAutoCommit(), "Pool must restore the legacy auto-commit setting");
            assertEquals("1", scalar(reused,
                    "SELECT TREATE_MK FROM SMS.KCB_CERT WHERE TRAN_SERNO = " + serial));
        }
        assertEquals("1", certificatePhase(), "Uncommitted work must remain rolled back outside the pool");
    }

    @Test
    void exhaustedRealOraclePoolReturnsWithinBoundAndRecoversAfterReturn() throws Exception {
        try (Connection held = borrowed()) {
            assertFalse(held.isClosed());
            long started = System.nanoTime();
            assertTimeoutPreemptively(Duration.ofSeconds(4), () ->
                    assertNull(db.getConnection(), "Current DBFactory returns null when the pool is exhausted"));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMs >= 250 && elapsedMs < 4000, "Acquisition must obey the configured bounded wait");
        }
        try (Connection recovered = borrowed()) {
            assertEquals("1", scalar(recovered, "SELECT 1 FROM DUAL"));
        }
    }

    @Test
    void legacyCharacterizationSecondWriteFailureStillReturnsTrueAndLeavesFirstWriteDurable() throws Exception {
        assertFailureInjectionConstraint();
        seedCertificate();
        boolean reportedSuccess = dao.updateStep1(serial, currentDtm, header(), response(FAIL_SECOND_WRITE));
        assertTrue(reportedSuccess,
                "Characterization of an existing defect: this passing assertion does not approve partial success");
        assertEquals("9", certificatePhase(), "First update is already durable because auto-commit is enabled");
        assertEquals("0", scalarFresh("SELECT COUNT(*) FROM SMS.KCB_INFO WHERE TRAN_SERNO = " + serial),
                "Second insert must have failed against the actual Oracle constraint");
    }

    @Test
    @Tag("readiness")
    void readinessSecondWriteFailureMustNotReportSuccessfulPersistence() throws Exception {
        assertFailureInjectionConstraint();
        seedCertificate();
        boolean reportedSuccess = dao.updateStep1(serial, currentDtm, header(), response(FAIL_SECOND_WRITE));
        assertEquals("9", certificatePhase(), "Confirm the first real Oracle update was attempted");
        assertEquals("0", scalarFresh("SELECT COUNT(*) FROM SMS.KCB_INFO WHERE TRAN_SERNO = " + serial),
                "Confirm the second real Oracle insert failed");
        assertFalse(reportedSuccess, "Failed second write must not be reported as successful persistence");
    }

    private void assertFailureInjectionConstraint() throws SQLException {
        assertEquals("1", scalarFresh("SELECT COUNT(*) FROM USER_CONSTRAINTS "
                + "WHERE TABLE_NAME = 'KCB_INFO' AND CONSTRAINT_NAME = 'CK_KCB_INFO_TEST_FAIL' "
                + "AND STATUS = 'ENABLED'"), "The deliberate synthetic failure constraint must exist");
    }

    private void seedCertificate() {
        assertTrue(dao.insert(serial, "TEST_ACTIVE", "SYNTHETIC", currentDtm, request()),
                "Synthetic certificate setup must succeed through the actual DAO");
    }

    private String certificatePhase() throws SQLException {
        return scalarFresh("SELECT TREATE_MK FROM SMS.KCB_CERT WHERE TRAN_SERNO = " + serial);
    }

    private KCBCertReqBean request() {
        KCBCertReqBean request = new KCBCertReqBean();
        request.setMobileStep("STEP1");
        request.setTid("SYNTHETIC_" + serial);
        request.setMobileComm("1");
        request.setMobileNum("00000000000");
        request.setMobileSsn("0000000000000");
        request.setAuthMode("NOSMS");
        request.setAuthNumber("000000");
        request.setCallBack("00000000000");
        request.setMertName(KOREAN_MERCHANT);
        request.setNameCheckYn("N");
        request.setBuyer(KOREAN_NAME);
        return request;
    }

    private KCBHeaderBean header() {
        KCBHeaderBean header = new KCBHeaderBean();
        header.setTranResponseCode("0000");
        header.setKcbTranSerno("123456SYNTHETIC");
        header.setKcbTranDtm(currentDtm);
        return header;
    }

    private KCBCertResBean response(String syntheticDi) {
        KCBCertResBean response = new KCBCertResBean();
        response.setCertMode("S");
        response.setResponseCode("0000");
        response.setResponseMsg(KOREAN_RESPONSE);
        response.setPersonalInfoCheckFlag("Y");
        response.setCi("SYNTHETIC_CI");
        response.setDi(syntheticDi);
        return response;
    }

    private Connection borrowed() {
        Connection connection = db.getConnection();
        assertNotNull(connection, "Disposable local Oracle pool must provide a real connection");
        return connection;
    }

    private Connection freshConnection() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "3000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "5000");
        return DriverManager.getConnection(url, properties);
    }

    private void assertSafeDatabase(Connection connection) throws SQLException {
        assertEquals(pdb.toUpperCase(java.util.Locale.ROOT),
                scalar(connection, "SELECT SYS_CONTEXT('USERENV', 'CON_NAME') FROM DUAL"),
                "Connected container must be the disposable test PDB");
        assertEquals("SMS", scalar(connection, "SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') FROM DUAL"),
                "Connected owner must be the synthetic SMS account");
    }

    private String scalarFresh(String sql) throws SQLException {
        try (Connection connection = freshConnection()) {
            return scalar(connection, sql);
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next(), "Expected one synthetic database result");
            return rows.getString(1);
        }
    }

    private void deleteRow(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Integer.parseInt(serial));
            statement.executeUpdate();
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertNotNull(value, "Missing " + name + "; run tools/tests/verify-oracle.sh");
        assertFalse(value.codePoints().allMatch(Character::isWhitespace), "Empty " + name + "; run tools/tests/verify-oracle.sh");
        return value;
    }

    private static void resetFactory() throws Exception {
        DBFactory.shutdown();
        setFactory("dataSource", null);
        setFactory("instance", null);
        setFactory("configBean", null);
    }

    private static Object getFactory(String name) throws Exception {
        Field field = DBFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setFactory(String name, Object value) throws Exception {
        Field field = DBFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
