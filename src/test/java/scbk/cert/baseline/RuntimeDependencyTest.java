package scbk.cert.baseline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Paths;
import java.sql.Driver;
import org.junit.jupiter.api.Test;

/** Loads the real selected JDBC artifact; never calls Driver.connect(). */
class RuntimeDependencyTest {
    @Test
    void selectedOracleDriverLoadsOnTheBuildRuntimeWithoutConnecting() throws Exception {
        Class<?> type = Class.forName("oracle.jdbc.OracleDriver");
        Driver driver = (Driver) type.getDeclaredConstructor().newInstance();
        assertTrue(driver.getMajorVersion() >= 23);
        String artifact = "1.8".equals(System.getProperty("java.specification.version"))
                ? "ojdbc8-" : "ojdbc11-";
        String loadedJar = Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getFileName().toString();
        assertTrue(loadedJar.startsWith(artifact) && loadedJar.endsWith(".jar"),
                "Load the profile's real Oracle JDBC jar, not a stub or the other runtime's jar");
    }
}
