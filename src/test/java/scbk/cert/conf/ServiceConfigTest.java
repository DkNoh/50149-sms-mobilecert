package scbk.cert.conf;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import scbk.cert.support.ServiceTestConfig;

class ServiceConfigTest {
    @Test
    void 테스트코드1_KCB설정과한글응답메시지를_정상조회한다() throws Exception {
        // given
        Path file = Paths.get(getClass().getResource("/service-test.xml").toURI());

        // when
        KCBConfigBean actual = ServiceTestConfig.load(file).kcb();

        // then
        assertEquals("127.0.0.1", actual.getKcbIp());
        assertEquals("0", actual.getKcbPort());
        assertEquals("30000", actual.getKcbTimeout());
        assertEquals("", actual.getAckServer());
        assertEquals("SYN-TX", actual.getTxCode());
        assertEquals("SYNMEM", actual.getConnMemberCode());
        assertEquals("SYN-MEMBER", actual.getMemberCode());
        assertEquals("0700", actual.getTranKindCode());
        assertEquals("400", actual.getBizTypeCode());
        assertEquals("B", actual.getTranFlag());
        assertEquals("3", actual.getTimeOut());
        assertEquals("5", actual.getRetryCnt());
        assertEquals("SYN_CERT_HST_", actual.getCertTable());
        assertEquals("SYN_CERT_INFO_HST_", actual.getCertInfoTable());
        assertEquals("https://example.invalid", actual.getReqUrl());
        assertEquals("127.0.0.1", actual.getReqIp());
        assertEquals("Y", actual.getStep1Mode());
        assertEquals("Y", actual.getStep2Mode());
        assertEquals("0000", actual.getTestRespCode());
        assertEquals("인증성공", actual.getTestRespMsg());
    }

    @Test
    void 테스트코드2_데이터베이스설정을_정상조회한다() throws Exception {
        // given
        Path file = Paths.get(getClass().getResource("/service-test.xml").toURI());

        // when
        DBConfigBean actual = ServiceTestConfig.load(file).db();

        // then
        assertEquals("ORACLE", actual.getDbType());
        assertEquals("SYNTHETIC_POOL", actual.getPoolName());
        assertEquals("oracle.jdbc.driver.OracleDriver", actual.getJdbcDriver());
        assertEquals("jdbc:oracle:thin:@127.0.0.1:1:DISABLED", actual.getDbUrl());
        assertEquals("SYNTHETIC_USER", actual.getDbUser());
        assertEquals("SYNTHETIC_PASSWORD", actual.getDbPassword());
        assertEquals("2", actual.getConnection());
    }
}
