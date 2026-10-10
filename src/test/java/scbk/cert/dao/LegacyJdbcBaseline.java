package scbk.cert.dao;

import java.lang.reflect.Field;
import java.sql.Connection;
import scbk.cert.conf.DBConfigBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.bean.kcb.KCBCertResBean;

/** Characterizes the existing DAO result; it does not endorse partial success. */
public final class LegacyJdbcBaseline {
    public static DBConfigBean configuration() {
        DBConfigBean bean = new DBConfigBean();
        bean.setJdbcDriver(RecordingDriver.class.getName());
        bean.setDbUrl("jdbc:mobilecert-test:baseline");
        bean.setPoolName("SyntheticPool");
        bean.setConnection("2");
        return bean;
    }

    public static void installConfiguration() throws Exception {
        Field field = DBFactory.class.getDeclaredField("configBean");
        field.setAccessible(true);
        field.set(null, configuration());
    }

    public static void verify() throws Exception {
        DBFactory db = DBFactory.getInstance();
        Connection c = db.getConnection();
        check(c != null && c.getAutoCommit(), "borrowed connection uses auto-commit");
        db.close(c);
        check(RecordingDriver.rollbacks == 0, "return does not roll back auto-commit work");

        check(db.preparedExecuteUpdate("UPDATE SYNTHETIC SET VALUE=1") == 1, "helper update result");
        check(RecordingDriver.commits == 1 && RecordingDriver.durableWrites == 1, "helper commit behavior");
        RecordingDriver.executions = RecordingDriver.durableWrites = RecordingDriver.commits = 0;
        RecordingDriver.failStatement = 2;
        KCBHeaderBean header = new KCBHeaderBean();
        header.setTranResponseCode("0000");
        header.setKcbTranSerno("123456SYNTHETIC");
        header.setKcbTranDtm("20260915120000");
        KCBCertResBean response = new KCBCertResBean();
        response.setCertMode("S");
        response.setResponseCode("0000");
        response.setResponseMsg("SYNTHETIC");
        response.setPersonalInfoCheckFlag("Y");
        boolean result = new KCBDAO("SYNTHETIC_CERT", "SYNTHETIC_INFO")
                .updateStep1("123", "20260915120000", header, response);
        check(RecordingDriver.executions == 2, "two statements attempted");
        check(result, "legacy result is true after second statement fails");
        check(RecordingDriver.durableWrites == 1, "first write remains committed");
        check(RecordingDriver.commits == 0 && RecordingDriver.rollbacks == 0, "no explicit commit or rollback on failure");
        for (RecordingDriver.Record record : RecordingDriver.connections) {
            check(!record.autoCommitChanges.contains(false), "manual transactions were not introduced");
        }
        System.out.println("PASS legacy JDBC auto-commit, helper commit, partial failure");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        RecordingDriver.reset();
        installConfiguration();
        verify();
        // The legacy pool manager owns a non-daemon reaper thread.
        System.exit(0);
    }
}
