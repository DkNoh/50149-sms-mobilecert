package scbk.cert.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;

/** Business failure expectations. Existing defects deliberately leave this gate red. */
@Tag("readiness")
class DaoReadinessTest {
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
    void secondKcbWriteFailureMustNotReportSuccessfulPersistence() {
        RecordingDriver.failStatement = 2;
        KCBHeaderBean header = new KCBHeaderBean();
        header.setTranResponseCode("0000");
        header.setKcbTranSerno("123456SYNTHETIC");
        header.setKcbTranDtm("20261010120000");
        KCBCertResBean response = new KCBCertResBean();
        response.setCertMode("S");
        response.setResponseCode("0000");
        response.setResponseMsg("SYNTHETIC");
        response.setPersonalInfoCheckFlag("Y");

        boolean saved = new KCBDAO("SYNTHETIC_CERT", "SYNTHETIC_INFO")
                .updateStep1("123", "20261010120000", header, response);

        assertEquals(2, RecordingDriver.executions, "The second SQL statement must actually be attempted");
        assertAll(
                () -> assertFalse(saved, "Failed second SQL must not be reported as successful persistence"),
                () -> assertTrue(RecordingDriver.connections.stream()
                        .flatMap(c -> c.statements.stream()).allMatch(s -> s.closed)),
                () -> assertTrue(DBFactory.getInstance().dumpPoolInfo().contains("active=0")));
    }

    @ParameterizedTest(name = "{0} helper reports pool exhaustion as SQLException")
    @ValueSource(strings = {"prepared", "statement"})
    void exhaustedPoolMustProduceDatabaseExceptionInsteadOfNullDereference(String helper) throws Exception {
        DBFactory db = DBFactory.getInstance();
        try (Connection first = db.getConnection(); Connection second = db.getConnection()) {
            assertNotNull(first);
            assertNotNull(second);
            assertAll(
                    () -> assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                            assertThrows(SQLException.class, () -> {
                                if ("prepared".equals(helper)) {
                                    db.preparedExecuteUpdate("UPDATE SYNTHETIC SET VALUE=1");
                                } else {
                                    db.statementExecuteUpdate("UPDATE SYNTHETIC SET VALUE=1");
                                }
                            }, "Unavailable connections must surface as database failures")),
                    () -> assertEquals(0, RecordingDriver.executions),
                    () -> assertEquals(0, RecordingDriver.durableWrites));
        }
    }

    private static void set(String name, Object value) throws Exception {
        Field field = DBFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
