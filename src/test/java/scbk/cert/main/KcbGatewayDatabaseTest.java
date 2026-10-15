package scbk.cert.main;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.dao.DevelopmentDatabase;

/** 개발 DB에는 실제로 기록하고 외부 케이씨비 응답만 대체한다. */
@Tag("db")
@ResourceLock("개발DB설정")
@Timeout(60)
class KcbGatewayDatabaseTest {
    @Test
    void 테스트코드1_가입확인과본인인증결과를_실제DB에저장한다() {
        // given
        try (DevelopmentDatabase database = DevelopmentDatabase.open()) {
            KcbGatewaySupport gateway = gateway(database);
            KCBCertReqBean request = KcbGatewaySupport.requestBean("STEP1");
            // when
            byte[] first = gateway.execute(KcbGatewaySupport.request(request, database.department()));
            String firstPhase = database.certificate().phase();
            int firstInfoCount = database.infoCount();
            request.setTid(gateway.transactionId);
            request.setMobileStep("STEP2");
            byte[] second = gateway.execute(KcbGatewaySupport.request(request, database.department()));
            // then
            assertEquals("0000", KcbGatewaySupport.result(first));
            assertEquals("0000", KcbGatewaySupport.result(second));
            assertEquals("STEP1", firstPhase);
            assertEquals(1, firstInfoCount);
            assertEquals("STEP2", database.certificate().phase());
            assertEquals("9", database.certificate().treatment());
            assertEquals("0000", database.certificate().serviceCode());
            assertEquals(1, database.infoCount());
            assertEquals(2, gateway.exchanges);
        }
    }

    @Test
    void 테스트코드2_실제DB의만료이력은_인증요청을거절한다() {
        // given
        try (DevelopmentDatabase database = DevelopmentDatabase.open()) {
            KcbGatewaySupport gateway = gateway(database);
            KCBCertReqBean request = KcbGatewaySupport.requestBean("STEP2");
            request.setTid(database.request().getTid());
            int timeout = Integer.parseInt(database.config().kcb().getTimeOut());
            database.seedHistory(request.getTid(), 0, timeout + 2);
            int rowsBefore = database.ownedRowCount();
            // when
            byte[] result = gateway.execute(KcbGatewaySupport.request(request, database.department()));
            // then
            assertEquals("XXX2", KcbGatewaySupport.result(result));
            assertEquals(0, gateway.exchanges);
            assertEquals(rowsBefore, database.ownedRowCount());
        }
    }

    @Test
    void 테스트코드3_횟수집계대상이력이여섯건이면_인증요청을거절한다() {
        // given
        try (DevelopmentDatabase database = DevelopmentDatabase.open()) {
            KcbGatewaySupport gateway = gateway(database);
            KCBCertReqBean request = KcbGatewaySupport.requestBean("STEP2");
            request.setTid(database.request().getTid());
            // 기존 조회 조건에 맞는 이력을 준비하며 실제 반복 요청의 누적 동작을 뜻하지 않는다.
            database.seedHistory(request.getTid(), 6, 0);
            int rowsBefore = database.ownedRowCount();
            // when
            byte[] result = gateway.execute(KcbGatewaySupport.request(request, database.department()));
            // then
            assertEquals("XXX1", KcbGatewaySupport.result(result));
            assertEquals(0, gateway.exchanges);
            assertEquals(rowsBefore, database.ownedRowCount());
        }
    }

    @Test
    void 테스트코드4_기관거절응답을_실제DB에기록한다() {
        // given
        try (DevelopmentDatabase database = DevelopmentDatabase.open()) {
            KcbGatewaySupport gateway = gateway(database);
            gateway.responseCode = "B001";
            // when
            byte[] result = gateway.execute(KcbGatewaySupport.request(
                    KcbGatewaySupport.requestBean("STEP1"), database.department()));
            // then
            assertEquals("B001", KcbGatewaySupport.result(result));
            assertEquals("B001", database.certificate().serviceCode());
            assertEquals("9", database.certificate().treatment());
            assertEquals(1, database.infoCount());
        }
    }

    private static KcbGatewaySupport gateway(DevelopmentDatabase database) {
        // XML의 강제 성공 응답을 끄고 대역이 반환한 성공과 거절을 각각 검증한다.
        database.config().kcb().setStep1Mode("N");
        database.config().kcb().setStep2Mode("N");
        return new KcbGatewaySupport(database.config().kcb(), database::dao);
    }
}
