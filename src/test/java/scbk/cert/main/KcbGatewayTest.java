package scbk.cert.main;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.dao.KCBDAO;

class KcbGatewayTest {
    @Test
    void 테스트코드1_가입확인요청을_정상처리한다() {
        // given
        FakeDao dao = new FakeDao();
        KcbGatewaySupport gateway = gateway(dao);
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(KcbGatewaySupport.requestBean("STEP1"), "TEST"));
        // then
        assertEquals("0000", KcbGatewaySupport.result(result));
        assertEquals(2048, result.length);
        assertEquals(484, gateway.sentLength);
        assertEquals("BSX", gateway.sentMode);
        assertEquals(1, dao.inserts);
        assertEquals(1, dao.updates);
    }

    @Test
    void 테스트코드2_본인인증요청을_정상처리한다() {
        // given
        FakeDao dao = new FakeDao();
        KcbGatewaySupport gateway = gateway(dao);
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(KcbGatewaySupport.requestBean("STEP2"), "TEST"));
        // then
        assertEquals("0000", KcbGatewaySupport.result(result));
        assertEquals(234, gateway.sentLength);
        assertEquals("CNSS", gateway.sentMode);
        assertEquals(1, dao.updates);
    }

    @Test
    void 테스트코드3_필수값이없으면_요청을거절한다() {
        // given
        FakeDao dao = new FakeDao();
        KcbGatewaySupport gateway = gateway(dao);
        KCBCertReqBean request = KcbGatewaySupport.requestBean("STEP1");
        request.setMobileNum("");
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(request, "TEST"));
        // then
        assertEquals("XXX0", KcbGatewaySupport.result(result));
        assertEquals(0, gateway.exchanges);
        assertEquals(0, dao.inserts);
    }

    @Test
    void 테스트코드4_인증시도횟수를초과하면_요청을거절한다() {
        // given
        FakeDao dao = new FakeDao();
        dao.attempts = "6";
        KcbGatewaySupport gateway = gateway(dao);
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(KcbGatewaySupport.requestBean("STEP2"), "TEST"));
        // then
        assertEquals("XXX1", KcbGatewaySupport.result(result));
        assertEquals(0, gateway.exchanges);
        assertEquals(0, dao.inserts);
    }

    @Test
    void 테스트코드5_인증시간이만료되면_요청을거절한다() {
        // given
        FakeDao dao = new FakeDao();
        dao.minutes = "3";
        KcbGatewaySupport gateway = gateway(dao);
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(KcbGatewaySupport.requestBean("STEP2"), "TEST"));
        // then
        assertEquals("XXX2", KcbGatewaySupport.result(result));
        assertEquals(0, gateway.exchanges);
    }

    @Test
    void 테스트코드6_등록에실패하면_통신을호출하지않는다() {
        // given
        FakeDao dao = new FakeDao();
        dao.insertResult = false;
        KcbGatewaySupport gateway = gateway(dao);
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(KcbGatewaySupport.requestBean("STEP1"), "TEST"));
        // then
        assertEquals("XXX3", KcbGatewaySupport.result(result));
        assertEquals(0, gateway.exchanges);
        assertEquals(0, dao.updates);
    }

    @Test
    void 테스트코드7_유효하지않은부서는_요청을거절한다() {
        // given
        FakeDao dao = new FakeDao();
        dao.departmentExists = false;
        KcbGatewaySupport gateway = gateway(dao);
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(KcbGatewaySupport.requestBean("STEP1"), "TEST"));
        // then
        assertEquals("XXX4", KcbGatewaySupport.result(result));
        assertEquals(0, gateway.exchanges);
        assertEquals(0, dao.inserts);
    }

    @Test
    void 테스트코드8_기관거절응답을_그대로반환한다() {
        // given
        FakeDao dao = new FakeDao();
        KcbGatewaySupport gateway = gateway(dao);
        gateway.responseCode = "B001";
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(KcbGatewaySupport.requestBean("STEP1"), "TEST"));
        // then
        assertEquals("B001", KcbGatewaySupport.result(result));
        assertEquals("B001", dao.savedCode);
    }

    @Test
    void 테스트코드9_기관성공응답을_내부성공코드로변환한다() {
        // given
        FakeDao dao = new FakeDao();
        KcbGatewaySupport gateway = gateway(dao);
        gateway.responseCode = "B000";
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(KcbGatewaySupport.requestBean("STEP1"), "TEST"));
        // then
        assertEquals("0000", KcbGatewaySupport.result(result));
        assertEquals("0000", dao.savedCode);
    }

    @Test
    void 테스트코드10_강제응답설정은_통신대역호출후적용한다() {
        // given
        FakeDao dao = new FakeDao();
        KCBConfigBean configuration = KcbGatewaySupport.configuration();
        configuration.setStep1Mode("Y");
        configuration.setTestRespCode("0000");
        configuration.setTestRespMsg("인증성공");
        KcbGatewaySupport gateway = new KcbGatewaySupport(configuration, (table, info) -> dao);
        gateway.responseCode = "B001";
        // when
        byte[] result = gateway.execute(KcbGatewaySupport.request(KcbGatewaySupport.requestBean("STEP1"), "TEST"));
        // then
        assertEquals(1, gateway.exchanges);
        assertEquals("0000", KcbGatewaySupport.result(result));
        assertEquals("0000", dao.savedCode);
    }

    private static KcbGatewaySupport gateway(FakeDao dao) {
        return new KcbGatewaySupport(KcbGatewaySupport.configuration(), (table, info) -> dao);
    }

    private static final class FakeDao extends KCBDAO {
        String attempts = "0";
        String minutes = "0";
        String savedCode;
        boolean departmentExists = true;
        boolean insertResult = true;
        int inserts;
        int updates;
        FakeDao() { super("TEST_CERT", "TEST_INFO"); }
        @Override public String getSerno() { return "1"; }
        @Override public boolean selectDeptCode(String department) { return departmentExists; }
        @Override public String[] selectCheck(KCBCertReqBean request) {
            return new String[] {attempts, "20260101000000", minutes};
        }
        @Override public boolean insert(String serial, String department, String office, String date, KCBCertReqBean request) {
            inserts++;
            return insertResult;
        }
        @Override public boolean updateStep1(String serial, String date, KCBHeaderBean header, KCBCertResBean response) {
            updates++;
            savedCode = response.getResponseCode();
            return true;
        }
    }
}
