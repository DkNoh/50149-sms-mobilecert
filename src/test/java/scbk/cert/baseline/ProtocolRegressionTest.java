package scbk.cert.baseline;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import scbk.cert.util.CertCharset;

class ProtocolRegressionTest {
    @Test
    void java8GoldenBytesRemainIdenticalWithUtf8JvmDefault() throws Exception {
        String previous = System.getProperty("scbk.cert.test.defaultCharset");
        try {
            System.setProperty("scbk.cert.test.defaultCharset", "UTF-8");
            ProtocolBaseline.main(new String[0]);
        } finally {
            if (previous == null) System.clearProperty("scbk.cert.test.defaultCharset");
            else System.setProperty("scbk.cert.test.defaultCharset", previous);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bothStandardStreamsRemainEucKrWithDefaultOrUtf8Jvm(boolean explicitUtf8) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString());
        if (explicitUtf8) command.add("-Dfile.encoding=UTF-8");
        command.addAll(java.util.Arrays.asList("-cp", System.getProperty("java.class.path"),
                RuntimeCharsetProbe.class.getName(), explicitUtf8 ? "UTF-8" : "default"));
        ProcessBuilder builder = new ProcessBuilder(command);
        for (String option : java.util.Arrays.asList("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) {
            builder.environment().remove(option);
        }
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(),
                    "Explicit stream encoding must work with UTF-8 JVM defaults; child stderr: "
                    + (process.exitValue() == 0 ? "" : new String(
                            scbk.cert.support.TestIo.readAll(process.getErrorStream()), java.nio.charset.StandardCharsets.UTF_8)));
            byte[] expected = "\uD55C\uAE00".getBytes(Charset.forName("EUC-KR"));
            assertArrayEquals(expected, scbk.cert.support.TestIo.readAll(process.getInputStream()));
            assertArrayEquals(expected, scbk.cert.support.TestIo.readAll(process.getErrorStream()));
        } finally { process.destroyForcibly(); }
    }

    public static final class RuntimeCharsetProbe {
        public static void main(String[] args) {
            Charset originalDefault = Charset.defaultCharset();
            if ("UTF-8".equals(args[0])) requireUtf8Default();
            CertCharset.configureStandardStreams();
            if (!originalDefault.equals(Charset.defaultCharset())) {
                throw new AssertionError("Stream configuration changed the JVM default charset");
            }
            System.out.print("\uD55C\uAE00");
            System.err.print("\uD55C\uAE00");
        }

        private static void requireUtf8Default() {
            if (!Charset.defaultCharset().equals(java.nio.charset.StandardCharsets.UTF_8)) {
                throw new AssertionError("Expected UTF-8 JVM default");
            }
        }
    }
}
