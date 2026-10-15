package scbk.cert.main;

import java.util.function.BiFunction;
import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.bean.mci.MCIHeaderBean;
import scbk.cert.bean.mci.MCITransBean;
import scbk.cert.conf.ConfigBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.dao.KCBDAO;
import scbk.cert.util.CertCharset;
import scbk.cert.util.CommonUtil;

/** 소켓을 열지 않고 실제 게이트웨이의 케이씨비 업무 경로를 실행한다. */
final class KcbGatewaySupport extends Gateway {
    private final BiFunction<String, String, KCBDAO> daoFactory;
    String responseCode = "0000";
    int exchanges;
    int sentLength;
    String sentMode;
    String transactionId;

    KcbGatewaySupport(KCBConfigBean configuration, BiFunction<String, String, KCBDAO> daoFactory) {
        super(new ConfigBean(), configuration, "SYNTHETIC");
        this.daoFactory = daoFactory;
    }

    @Override protected String selectProvider(String transactionCode) {
        return "KCB";
    }

    @Override protected KCBDAO createKcbDao(String table, String infoTable) {
        return daoFactory.apply(table, infoTable);
    }

    @Override protected KCBCommunication createKcbCommunication(KCBConfigBean configuration, String requestId) {
        return new KCBCommunication(configuration, requestId) {
            @Override public byte[] socketComm(String request, String department) {
                exchanges++;
                byte[] bytes = request.getBytes(CertCharset.EUC_KR);
                sentLength = bytes.length;
                sentMode = new String(bytes, 134, 4, CertCharset.EUC_KR).trim();
                transactionId = new String(bytes, 150, 30, CertCharset.EUC_KR).trim();
                KCBHeaderBean header = new KCBHeaderBean();
                header.setTranCode("ACSEV");
                header.setTranResponseCode("0000");
                header.setKcbTranSerno("0000010");
                header.setKcbTranDtm(CommonUtil.getCurrentDate("yyyyMMddHHmmss"));
                KCBCertResBean body = new KCBCertResBean();
                body.setTranMode(sentMode);
                body.setTranNo(transactionId);
                body.setResponseCode(responseCode);
                body.setResponseMsg("테스트 응답");
                body.setCertMode("P");
                body.setPersonalInfoCheckFlag("Y");
                return (header.getTransaction() + body.getTransaction()).getBytes(CertCharset.EUC_KR);
            }
        };
    }

    static byte[] request(KCBCertReqBean body, String department) {
        MCIHeaderBean header = new MCIHeaderBean();
        header.setTranCd("TEST-KCB");
        MCITransBean transaction = new MCITransBean();
        String data = CommonUtil.byteFiller(department, 12) + CommonUtil.byteFiller("TEST", 12)
                + CommonUtil.setFiller(164) + body.getTransaction();
        return (header.getTransaction() + transaction.getTransaction() + '\u001e' + '\u001e'
                + data + '\u001e').getBytes(CertCharset.EUC_KR);
    }

    static String result(byte[] response) {
        return new String(response, 159, 4, CertCharset.EUC_KR);
    }

    static KCBCertReqBean requestBean(String step) {
        KCBCertReqBean request = new KCBCertReqBean();
        request.setMobileStep(step);
        request.setTid("CERT20260101000000000001");
        request.setMobileComm("1");
        request.setMobileNum("00000000000");
        request.setMobileSsn("0000001000000");
        request.setAuthMode("NOSMS");
        request.setNameCheckYn("N");
        request.setBuyer("TEST");
        request.setMertName("TEST");
        return request;
    }

    static KCBConfigBean configuration() {
        KCBConfigBean configuration = new KCBConfigBean();
        configuration.setCertTable("TEST_CERT_");
        configuration.setCertInfoTable("TEST_INFO_");
        configuration.setTimeOut("3");
        configuration.setRetryCnt("5");
        configuration.setReqUrl("http://example.invalid");
        configuration.setReqIp("192.0.2.1");
        configuration.setStep1Mode("N");
        configuration.setStep2Mode("N");
        return configuration;
    }
}
