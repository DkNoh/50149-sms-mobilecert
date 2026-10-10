package scbk.cert.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import scbk.cert.conf.ConfigBean;
import scbk.cert.util.CertCharset;
import scbk.cert.util.Log;
import scbk.cert.util.XMLFactory;

class ServiceLauncherStartupTest {
    private static final String KOREAN_TEXT = "\uD55C\uAE00 \uC124\uC815 \uD655\uC778";

    @TempDir
    Path temporaryDirectory;

    @ParameterizedTest(name = "UTF-8 JVM starts with {0} XML and preserves Korean log text")
    @ValueSource(strings = {"UTF-8", "EUC-KR"})
    @Timeout(30)
    void utf8RuntimeLoadsDeclaredXmlCharsetAndStartsListener(String charsetName) throws Exception {
        Charset fixtureCharset = Charset.forName(charsetName);
        Path runDirectory = Files.createDirectories(temporaryDirectory.resolve("run"));
        Path confDirectory = Files.createDirectories(temporaryDirectory.resolve("conf"));
        Path logFile = temporaryDirectory.resolve("startup.log");
        Path logConfiguration = temporaryDirectory.resolve("log4j2-test.xml");
        Path stdout = temporaryDirectory.resolve("stdout.txt");
        Path stderr = temporaryDirectory.resolve("stderr.txt");

        // Synthetic startup fixture only: no provider, database or operational configuration.
        String xml = "<?xml version=\"1.0\" encoding=\"" + charsetName + "\"?>\n"
                + "<!DOCTYPE service [\n"
                + "<!ELEMENT service (entity)>\n"
                + "<!ELEMENT entity (serverPort,txName)>\n"
                + "<!ATTLIST entity name CDATA #REQUIRED class CDATA #REQUIRED>\n"
                + "<!ELEMENT serverPort (#PCDATA)>\n"
                + "<!ELEMENT txName (#PCDATA)>\n"
                + "]>\n"
                + "<service><entity name=\"XPAY_LEASED\" class=\"scbk.cert.conf.ConfigBean\">"
                + "<serverPort>0</serverPort><txName>" + KOREAN_TEXT + "</txName>"
                + "</entity></service>\n";
        scbk.cert.support.TestIo.writeString(confDirectory.resolve("service-charset-test.xml"), xml, fixtureCharset);
        String loggingXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Configuration status=\"ERROR\"><Appenders>"
                + "<File name=\"TestFile\" fileName=\"" + xmlAttribute(logFile.toString())
                + "\" append=\"false\"><PatternLayout charset=\"" + charsetName
                + "\" pattern=\"%msg%n\"/></File></Appenders>"
                + "<Loggers><Root level=\"error\"><AppenderRef ref=\"TestFile\"/>"
                + "</Root></Loggers></Configuration>\n";
        scbk.cert.support.TestIo.writeString(logConfiguration, loggingXml, StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(
                java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8",
                "-Dscbk.cert.server.type=charset-test",
                "-Dlog4j.configurationFile=" + logConfiguration.toUri(),
                "-cp", absoluteClasspath(), StartupProbe.class.getName())
                .directory(runDirectory.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile());
        for (String option : java.util.Arrays.asList("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) {
            builder.environment().remove(option);
        }
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS),
                    "Isolated startup/shutdown must finish within 15 seconds");
            assertEquals(0, process.exitValue(), () -> childDiagnostics(stdout, stderr));
            assertTrue(scbk.cert.support.TestIo.readString(stdout, StandardCharsets.US_ASCII).contains("STARTUP_OK"));
            assertTrue(Arrays.asList(scbk.cert.support.TestIo.readString(logFile, fixtureCharset).split("\\r?\\n")).contains(KOREAN_TEXT),
                    "Log.write must preserve Korean text with the explicitly configured log charset");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Timed-out child must be reaped");
            }
        }
    }

    private static String absoluteClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(entry -> java.nio.file.Paths.get(entry).toAbsolutePath().normalize().toString())
                .collect(Collectors.joining(File.pathSeparator));
    }

    private static String xmlAttribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String childDiagnostics(Path stdout, Path stderr) {
        try {
            String diagnostic = "Child stdout:\n" + scbk.cert.support.TestIo.readString(stdout, StandardCharsets.UTF_8)
                    + "\nChild stderr:\n" + scbk.cert.support.TestIo.readString(stderr, StandardCharsets.UTF_8);
            return diagnostic.substring(0, Math.min(diagnostic.length(), 8192));
        } catch (Exception failure) {
            return "Isolated startup failed; child output could not be decoded: "
                    + failure.getClass().getSimpleName();
        }
    }

    public static final class StartupProbe {
        public static void main(String[] args) throws Exception {
            check(StandardCharsets.UTF_8.equals(Charset.defaultCharset()), "Expected UTF-8 JVM default");
            CertCharset.configureStandardStreams();
            ServiceLauncher launcher = new ServiceLauncher();
            ServerSocket listener = null;
            Thread serviceThread = null;
            try {
                check(launcher.start(), "ServiceLauncher.start must succeed");
                ConfigBean configuration = (ConfigBean) XMLFactory.getEntity("XPAY_LEASED");
                check(KOREAN_TEXT.equals(configuration.getTxName()), "Korean XML text must survive parsing");
                check("0".equals(configuration.getServerPort()), "Only an ephemeral listener is allowed");
                SocketService service = (SocketService) field(launcher, "service");
                listener = (ServerSocket) field(service, "serverSocket");
                serviceThread = (Thread) field(service, "serviceThread");
                check(listener.isBound() && !listener.isClosed() && listener.getLocalPort() > 0,
                        "The service must bind an ephemeral listener");
                check(serviceThread.isAlive(), "The listener thread must be running");
                Log.write("log.day", KOREAN_TEXT, launcher);
            } finally {
                try {
                    launcher.close();
                } finally {
                    org.apache.logging.log4j.LogManager.shutdown();
                }
            }
            check(listener != null && listener.isClosed(), "Service close must close the listener");
            check(serviceThread != null && !serviceThread.isAlive(), "Service close must stop its thread");
            System.out.println("STARTUP_OK");
        }

        private static Object field(Object target, String name) throws Exception {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }

        private static void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
    }
}
