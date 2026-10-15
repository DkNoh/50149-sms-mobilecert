package scbk.cert.dao;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariDataSource;
import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.conf.DBConfigBean;
import scbk.cert.support.ServiceTestConfig;

/** 개발 DB의 기존 테이블만 사용하고 이번 실행이 만든 행만 정리한다. */
public final class DevelopmentDatabase implements AutoCloseable {
    private static final String TIMEOUT_PROPERTY = "scbk.cert.db.connectionTimeoutMs";
    private static final String NAME = "가상검증사용자";
    private static final String MERCHANT = "인증시험상점";
    private static final String RESPONSE = "인증성공";
    private final ServiceTestConfig config;
    private final Map<String, Object> previousFactory = new HashMap<String, Object>();
    private final Map<Logger, Level> previousLevels = new LinkedHashMap<Logger, Level>();
    private final List<OwnedRow> rows = new ArrayList<OwnedRow>();
    private final Set<String> issuedSerials = new HashSet<String>();
    private final String previousTimeout;
    private HikariDataSource pool;
    private String currentDtm;
    private String table;
    private String infoTable;
    private String department;
    private KCBDAO dao;
    private boolean restored;

    private DevelopmentDatabase(ServiceTestConfig config) {
        this.config = config;
        this.previousTimeout = System.getProperty(TIMEOUT_PROPERTY);
    }

    public static DevelopmentDatabase open() {
        DevelopmentDatabase database = new DevelopmentDatabase(ServiceTestConfig.load());
        try {
            database.initialize();
            return database;
        } catch (Exception failure) {
            database.restore();
            throw databaseFailure("개발 DB 준비", failure);
        } catch (AssertionError failure) {
            database.restore();
            throw failure;
        }
    }

    private void initialize() throws Exception {
        muteConnectionLogs();
        for (String name : new String[] {"configBean", "instance", "dataSource"}) {
            previousFactory.put(name, field(name).get(null));
        }
        DBConfigBean bean = new DBConfigBean();
        bean.setDbType(config.db().getDbType());
        bean.setJdbcDriver(config.db().getJdbcDriver());
        bean.setDbUrl(config.db().getDbUrl());
        bean.setDbUser(config.db().getDbUser());
        bean.setDbPassword(config.db().getDbPassword());
        bean.setConnection("2");
        bean.setPoolName("mobilecert-junit");
        System.setProperty(TIMEOUT_PROPERTY, "5000");
        pool = DBFactory.createDataSource(bean);
        pool.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "3000");
        pool.addDataSourceProperty("oracle.jdbc.ReadTimeout", "5000");
        pool.setValidationTimeout(1000);
        field("configBean").set(null, bean);
        field("dataSource").set(null, pool);
        field("instance").set(null, null);
        DBFactory.getInstance();
        currentDtm = scalar("SELECT TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS') FROM DUAL");
        require(currentDtm != null && currentDtm.matches("[0-9]{14}"), "개발 DB의 현재 시각을 읽지 못했습니다.");
        require(currentDtm.substring(0, 6).equals(new SimpleDateFormat("yyyyMM").format(new Date())),
                "개발 DB와 테스트 실행 환경의 월이 다릅니다. 월별 테이블을 확인하세요.");
        table = config.kcb().getCertTable() + currentDtm.substring(0, 6);
        infoTable = config.kcb().getCertInfoTable() + currentDtm.substring(0, 6);
        verifyExistingTables();
        department = scalar("SELECT DEP_ID FROM SMS.DEP WHERE ACT_YN = 'Y' AND LENGTH(DEP_ID) <= 12 AND ROWNUM = 1");
        require(department != null && department.matches("[A-Za-z0-9_-]{1,12}"),
                "기존 개발 부서 중 테스트에 사용할 활성 부서가 필요합니다.");
        dao = new TrackedDao();
    }

    public ServiceTestConfig config() {
        return config;
    }

    public KCBDAO dao() {
        return dao;
    }

    public KCBDAO dao(String certificateTable, String certificateInfoTable) {
        require(table.equals(certificateTable) && infoTable.equals(certificateInfoTable),
                "업무 코드가 설정과 다른 월별 테이블을 요청했습니다.");
        return dao;
    }

    public String currentDtm() {
        return currentDtm;
    }

    public String department() {
        return department;
    }

    public String latestSerial() {
        return latest().serial;
    }

    public KCBCertReqBean request() {
        KCBCertReqBean request = new KCBCertReqBean();
        request.setMobileStep("STEP1");
        request.setTid("JUNIT" + UUID.randomUUID().toString().replace("-", "").substring(0, 19));
        request.setMobileComm("1");
        request.setMobileNum("00000000000");
        request.setMobileSsn("0000000000000");
        request.setAuthMode("NOSMS");
        request.setAuthNumber("000000");
        request.setCallBack("00000000000");
        request.setMertName(MERCHANT);
        request.setNameCheckYn("N");
        request.setBuyer(NAME);
        return request;
    }

    public KCBHeaderBean header() {
        KCBHeaderBean header = new KCBHeaderBean();
        header.setTranResponseCode("0000");
        header.setKcbTranSerno("123456JUNIT");
        header.setKcbTranDtm(currentDtm);
        return header;
    }

    public KCBCertResBean response() {
        KCBCertResBean response = new KCBCertResBean();
        response.setCertMode("S");
        response.setResponseCode("0000");
        response.setResponseMsg(RESPONSE);
        response.setPersonalInfoCheckFlag("Y");
        response.setCi("JUNIT_CI");
        response.setDi("JUNIT_DI");
        return response;
    }

    public boolean usesOracleConnection() {
        try (Connection connection = fresh()) {
            return "Oracle".equals(connection.getMetaData().getDatabaseProductName())
                    && connection.getMetaData().getDriverName().contains("Oracle");
        } catch (Exception failure) {
            throw databaseFailure("Oracle 연결 검증", failure);
        }
    }

    public Snapshot certificate() {
        OwnedRow owned = latest();
        String sql = "SELECT CERT_PRGRSS_PHASE_MK, TREATE_MK, TRAN_RSPNS_CD, TRAN_SVC_RSPNS_CD, NM, MRCHNT_NM "
                + "FROM SMS." + table + " WHERE TRAN_DT = ? AND TRAN_SERNO = ? AND TRAN_NO = ?";
        try (Connection connection = fresh(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindOwned(statement, owned);
            try (ResultSet results = statement.executeQuery()) {
                require(results.next(), "이번 테스트가 등록한 인증 이력을 조회하지 못했습니다.");
                Snapshot snapshot = new Snapshot(results.getString(1), results.getString(2),
                        results.getString(3), results.getString(4), NAME.equals(results.getString(5)),
                        MERCHANT.equals(results.getString(6)));
                require(!results.next(), "이번 테스트의 인증 이력이 중복되었습니다.");
                return snapshot;
            }
        } catch (Exception failure) {
            throw databaseFailure("등록 결과 조회", failure);
        }
    }

    public int infoCount() {
        return count(infoTable, latest());
    }

    public boolean infoMatchesSyntheticResponse() {
        OwnedRow owned = latest();
        String sql = "SELECT ETC1, ETC2 FROM SMS." + infoTable + " WHERE TRAN_DT = ? AND TRAN_SERNO = ?";
        try (Connection connection = fresh(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindKey(statement, owned);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() && "JUNIT_CI".equals(results.getString(1))
                        && "JUNIT_DI".equals(results.getString(2)) && !results.next();
            }
        } catch (Exception failure) {
            throw databaseFailure("인증 부가 이력 조회", failure);
        }
    }

    /** 만료와 횟수 제한 검증에 필요한 이력만 생성한다. */
    public void seedHistory(String tid, int successfulAttempts, int minutesAgo) {
        require(successfulAttempts >= 0 && successfulAttempts <= 100 && minutesAgo >= 0 && minutesAgo <= 43200,
                "테스트 이력 범위가 올바르지 않습니다.");
        String timestamp;
        try (Connection connection = fresh(); PreparedStatement statement = connection.prepareStatement(
                "SELECT TO_CHAR(SYSDATE - (? / 1440), 'YYYYMMDDHH24MISS') FROM DUAL")) {
            statement.setInt(1, minutesAgo);
            try (ResultSet results = statement.executeQuery()) {
                require(results.next(), "테스트 기준 시각을 읽지 못했습니다.");
                timestamp = results.getString(1);
            }
        } catch (Exception failure) {
            throw databaseFailure("테스트 기준 시각 조회", failure);
        }
        for (int index = 0; index < Math.max(1, successfulAttempts); index++) {
            KCBCertReqBean request = request();
            request.setTid(tid);
            request.setMobileStep(successfulAttempts > 0 ? "STEP2" : "STEP1");
            require(dao.insert(dao.getSerno(), department, "JUNIT", timestamp, request),
                    "업무 테스트에 필요한 인증 이력을 등록하지 못했습니다.");
            if (successfulAttempts > 0) {
                markLatestSuccessful();
            }
        }
    }

    public void markLatestSuccessful() {
        OwnedRow owned = latest();
        String sql = "UPDATE SMS." + table + " SET CERT_PRGRSS_PHASE_MK = 'STEP2', TREATE_MK = 'S' "
                + "WHERE TRAN_DT = ? AND TRAN_SERNO = ? AND TRAN_NO = ?";
        try (Connection connection = fresh(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindOwned(statement, owned);
            require(statement.executeUpdate() == 1, "이번 테스트의 인증 이력 상태를 변경하지 못했습니다.");
        } catch (Exception failure) {
            throw databaseFailure("테스트 이력 상태 변경", failure);
        }
    }

    public int ownedRowCount() {
        return rows.size();
    }

    private void verifyExistingTables() {
        for (String name : new String[] {table, infoTable}) {
            try (Connection connection = fresh(); PreparedStatement statement = connection.prepareStatement(
                    "SELECT TRAN_DT, TRAN_SERNO FROM SMS." + name + " WHERE 1 = 0");
                    ResultSet ignored = statement.executeQuery()) {
                // 기존 월별 테이블의 존재와 조회 권한만 확인한다.
            } catch (Exception failure) {
                throw databaseFailure("기존 월별 테이블 확인", failure);
            }
        }
    }

    private Connection fresh() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", config.db().getDbUser());
        properties.setProperty("password", config.db().getDbPassword());
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "3000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "5000");
        properties.setProperty("oracle.jdbc.autoCommitSpecCompliant", "false");
        return DriverManager.getConnection(config.db().getDbUrl(), properties);
    }

    private String scalar(String sql) {
        try (Connection connection = fresh(); PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            require(results.next(), "개발 DB에서 필요한 기준 정보를 조회하지 못했습니다.");
            return results.getString(1);
        } catch (Exception failure) {
            throw databaseFailure("개발 DB 기준 정보 조회", failure);
        }
    }

    private int count(String target, OwnedRow owned) {
        try (Connection connection = fresh(); PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM SMS." + target + " WHERE TRAN_DT = ? AND TRAN_SERNO = ?")) {
            bindKey(statement, owned);
            try (ResultSet results = statement.executeQuery()) {
                require(results.next(), "테스트 이력 수를 확인하지 못했습니다.");
                return results.getInt(1);
            }
        } catch (Exception failure) {
            throw databaseFailure("테스트 이력 수 조회", failure);
        }
    }

    @Override
    public void close() {
        AssertionError cleanupFailure = null;
        try {
            for (OwnedRow row : rows) {
                try {
                    cleanup(row);
                } catch (AssertionError failure) {
                    cleanupFailure = failure;
                }
            }
            if (pool != null && pool.getHikariPoolMXBean() != null
                    && pool.getHikariPoolMXBean().getActiveConnections() != 0) {
                cleanupFailure = new AssertionError("DAO가 사용한 DB 연결을 반환하지 않았습니다.");
            }
        } finally {
            restore();
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private void cleanup(OwnedRow owned) {
        try (Connection connection = fresh()) {
            connection.setAutoCommit(false);
            try {
                String infoSql = "DELETE FROM SMS." + infoTable + " I WHERE I.TRAN_DT = ? AND I.TRAN_SERNO = ? "
                        + "AND EXISTS (SELECT 1 FROM SMS." + table
                        + " C WHERE C.TRAN_DT = I.TRAN_DT AND C.TRAN_SERNO = I.TRAN_SERNO AND C.TRAN_NO = ?)";
                try (PreparedStatement statement = connection.prepareStatement(infoSql)) {
                    bindOwned(statement, owned);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM SMS." + table
                        + " WHERE TRAN_DT = ? AND TRAN_SERNO = ? AND TRAN_NO = ?")) {
                    bindOwned(statement, owned);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        } catch (Exception failure) {
            throw databaseFailure("이번 테스트 데이터 정리", failure);
        }
        require(count(DevelopmentDatabase.this.table, owned) == 0 && count(DevelopmentDatabase.this.infoTable, owned) == 0,
                "이번 테스트 데이터가 남았습니다. 정리 결과를 확인하세요.");
    }

    private void restore() {
        if (restored) {
            return;
        }
        restored = true;
        try {
            if (pool != null) {
                pool.close();
            }
            for (Map.Entry<String, Object> entry : previousFactory.entrySet()) {
                field(entry.getKey()).set(null, entry.getValue());
            }
        } catch (Exception failure) {
            throw new AssertionError("테스트 DB 연결 상태를 복원하지 못했습니다.");
        } finally {
            if (previousTimeout == null) {
                System.clearProperty(TIMEOUT_PROPERTY);
            } else {
                System.setProperty(TIMEOUT_PROPERTY, previousTimeout);
            }
            for (Map.Entry<Logger, Level> entry : previousLevels.entrySet()) {
                entry.getKey().setLevel(entry.getValue());
            }
        }
    }

    private void muteConnectionLogs() {
        for (String name : new String[] {"com.zaxxer.hikari", "oracle.jdbc", "oracle.net"}) {
            mute(Logger.getLogger(name));
        }
        Enumeration<String> names = LogManager.getLogManager().getLoggerNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.startsWith("com.zaxxer.hikari.") || name.startsWith("oracle.jdbc.") || name.startsWith("oracle.net.")) {
                Logger logger = LogManager.getLogManager().getLogger(name);
                if (logger != null) {
                    mute(logger);
                }
            }
        }
    }

    private void mute(Logger logger) {
        if (!previousLevels.containsKey(logger)) {
            previousLevels.put(logger, logger.getLevel());
            logger.setLevel(Level.OFF);
        }
    }

    private static Field field(String name) throws Exception {
        Field field = DBFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void bindKey(PreparedStatement statement, OwnedRow row) throws SQLException {
        statement.setString(1, row.day);
        statement.setInt(2, Integer.parseInt(row.serial));
    }

    private static void bindOwned(PreparedStatement statement, OwnedRow row) throws SQLException {
        bindKey(statement, row);
        statement.setString(3, row.tid);
    }

    private OwnedRow latest() {
        require(!rows.isEmpty(), "이번 테스트가 등록한 인증 이력이 없습니다.");
        return rows.get(rows.size() - 1);
    }

    private static AssertionError databaseFailure(String operation, Exception failure) {
        String details = "";
        if (failure instanceof SQLException) {
            SQLException sql = (SQLException) failure;
            String state = sql.getSQLState();
            details = " SQL 상태=" + (state != null && state.matches("[A-Z0-9]{5}") ? state : "미확인")
                    + ", 오류 코드=" + sql.getErrorCode();
        }
        return new AssertionError(operation + "에 실패했습니다. 개발 DB 연결, 권한과 기존 스키마를 확인하세요." + details);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private final class TrackedDao extends KCBDAO {
        private TrackedDao() {
            super(DevelopmentDatabase.this.table, DevelopmentDatabase.this.infoTable);
        }

        @Override
        public String getSerno() {
            String serial = super.getSerno();
            require(serial != null && serial.matches("[0-9]{1,10}"), "실제 DAO가 인증 순번을 조회하지 못했습니다.");
            long parsed = Long.parseLong(serial);
            require(parsed > 0 && parsed <= Integer.MAX_VALUE, "인증 순번이 기존 DAO의 정수 범위를 벗어났습니다.");
            issuedSerials.add(serial);
            return serial;
        }

        @Override
        public boolean insert(String serial, String dept, String office, String timestamp, KCBCertReqBean request) {
            require(issuedSerials.remove(serial), "이번 테스트에서 조회한 인증 순번만 사용할 수 있습니다.");
            require(timestamp != null && timestamp.matches("[0-9]{14}"), "테스트 인증 시각 형식이 올바르지 않습니다.");
            require(request.getTid() != null && !request.getTid().isEmpty(), "테스트 거래 식별자가 필요합니다.");
            OwnedRow owned = new OwnedRow(serial, timestamp.substring(0, 8), request.getTid());
            require(count(DevelopmentDatabase.this.table, owned) == 0 && count(DevelopmentDatabase.this.infoTable, owned) == 0,
                    "인증 순번이 기존 데이터와 겹칩니다. 테스트 등록을 중단합니다.");
            rows.add(owned);
            return super.insert(serial, dept, office, timestamp, request);
        }

        @Override
        public boolean updateStep1(String serial, String timestamp, KCBHeaderBean header, KCBCertResBean response) {
            boolean owned = false;
            for (OwnedRow row : rows) {
                if (row.serial.equals(serial) && timestamp != null && timestamp.startsWith(row.day)) {
                    owned = true;
                    break;
                }
            }
            require(owned, "이번 테스트가 등록한 인증 이력만 갱신할 수 있습니다.");
            return super.updateStep1(serial, timestamp, header, response);
        }
    }

    private static final class OwnedRow {
        private final String serial;
        private final String day;
        private final String tid;

        private OwnedRow(String serial, String day, String tid) {
            this.serial = serial;
            this.day = day;
            this.tid = tid;
        }
    }

    public static final class Snapshot {
        private final String phase;
        private final String treatment;
        private final String headerCode;
        private final String serviceCode;
        private final boolean nameMatches;
        private final boolean merchantMatches;

        private Snapshot(String phase, String treatment, String headerCode, String serviceCode,
                boolean nameMatches, boolean merchantMatches) {
            this.phase = phase;
            this.treatment = treatment;
            this.headerCode = headerCode;
            this.serviceCode = serviceCode;
            this.nameMatches = nameMatches;
            this.merchantMatches = merchantMatches;
        }

        public String phase() { return phase; }
        public String treatment() { return treatment; }
        public String headerCode() { return headerCode; }
        public String serviceCode() { return serviceCode; }
        public boolean nameMatches() { return nameMatches; }
        public boolean merchantMatches() { return merchantMatches; }
    }
}