package scbk.cert.conf;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import scbk.cert.dao.DevelopmentDatabase;
import scbk.cert.support.ServiceTestConfig;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ServiceConfigTest {
    @TempDir
    Path directory;
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

    @Test
    @Tag("config")
    void 테스트코드3_선택한서비스환경설정을_정상조회한다() {
        // given
        // 선택한 실제 XML을 읽기만 하며 DB와 인증 서버에는 접속하지 않는다.

        // when
        ServiceTestConfig actual = ServiceTestConfig.load();

        // then
        assertNotNull(actual.db());
        assertNotNull(actual.kcb());
    }

    @Test
    void 테스트코드4_운영설정은조회하되_DB연동은거절한다() throws Exception {
        // given
        Path file = directory.resolve("service-prod.xml");
        Files.copy(Paths.get(getClass().getResource("/service-test.xml").toURI()), file);
        String previous = System.getProperty("mobilecert.test.config");
        System.setProperty("mobilecert.test.config", file.toString());
        try {
            // when
            ServiceTestConfig actual = ServiceTestConfig.load();
            AssertionError failure = assertThrows(AssertionError.class, DevelopmentDatabase::open);

            // then
            assertNotNull(actual.db());
            assertNotNull(actual.kcb());
            assertEquals("DB 연동 테스트에는 service-dev.xml 개발 설정만 사용할 수 있습니다. 운영 설정은 config 프로파일로 검사하세요.",
                    failure.getMessage());
        } finally {
            if (previous == null) {
                System.clearProperty("mobilecert.test.config");
            } else {
                System.setProperty("mobilecert.test.config", previous);
            }
        }
    }

    @Test
    void 테스트코드5_개발설정은_DB연동대상으로허용한다() throws Exception {
        // given
        Path file = directory.resolve("service-dev.xml");
        Files.copy(Paths.get(getClass().getResource("/service-test.xml").toURI()), file);

        // when
        ServiceTestConfig actual = ServiceTestConfig.load(file);

        // then
        assertDoesNotThrow(actual::requireDevelopmentDatabase);
    }

    @Test
    void 테스트코드6_환경이확인되지않은설정은_DB연동을거절한다() throws Exception {
        // given
        Path file = Paths.get(getClass().getResource("/service-test.xml").toURI());

        // when
        ServiceTestConfig actual = ServiceTestConfig.load(file);

        // then
        assertThrows(AssertionError.class, actual::requireDevelopmentDatabase);
    }
}