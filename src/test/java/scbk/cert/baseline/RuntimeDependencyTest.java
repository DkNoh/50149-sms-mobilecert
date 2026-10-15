package scbk.cert.baseline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Paths;
import java.sql.Driver;
import org.junit.jupiter.api.Test;

class RuntimeDependencyTest {
    @Test
    void 테스트코드1_실행자바버전에맞는_오라클드라이버를불러온다() throws Exception {
        // given
        String artifact = "1.8".equals(System.getProperty("java.specification.version"))
                ? "ojdbc8-" : "ojdbc11-";

        // when
        Class<?> type = Class.forName("oracle.jdbc.OracleDriver");
        Driver driver = (Driver) type.getDeclaredConstructor().newInstance();
        String loadedJar = Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getFileName().toString();

        // then
        assertTrue(driver.getMajorVersion() >= 23);
        assertTrue(loadedJar.startsWith(artifact) && loadedJar.endsWith(".jar"),
                "실행 자바 버전에 맞는 실제 오라클 드라이버를 사용해야 한다.");
    }
}
