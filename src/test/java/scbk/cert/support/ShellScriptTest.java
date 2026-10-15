package scbk.cert.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Tag("shell")
@Timeout(30)
class ShellScriptTest {
    @TempDir
    Path temporaryDirectory;

    private Path project;
    private Path verificationScript;
    private Path launchScript;
    private Path trace;
    private Path fakeCommands;
    private Path fakeJavaHome;
    private String bash;
    private final Map<String, String> overrides = new HashMap<String, String>();

    @BeforeEach
    void 임시환경을준비한다() throws Exception {
        bash = findBash();
        project = Files.createDirectories(temporaryDirectory.resolve("project space"));
        Path toolDirectory = Files.createDirectories(project.resolve("tools/tests"));
        verificationScript = toolDirectory.resolve("verify-mobilecert.sh");
        launchScript = project.resolve("tools/run-mobilecert.sh");
        Path source = Paths.get("").toAbsolutePath();
        Files.copy(source.resolve("tools/tests/verify-mobilecert.sh"), verificationScript);
        Files.copy(source.resolve("tools/run-mobilecert.sh"), launchScript);
        trace = temporaryDirectory.resolve("arguments.txt");
        fakeCommands = Files.createDirectories(temporaryDirectory.resolve("commands space"));
        fakeJavaHome = Files.createDirectories(temporaryDirectory.resolve("jdk space/bin")).getParent();
        // 실제 Maven과 Java 대신 인자만 기록하고 지정한 종료 코드를 반환한다.
        writeCommand(fakeCommands.resolve("mvn"),
                "#!/usr/bin/env bash\n"
                + "if [ \"${1:-}\" = -version ]; then\n"
                + "  printf 'Java version: %s, vendor: test\\n' \"${TEST_JAVA_VERSION:-21.0.1}\"\n"
                + "  exit \"${TEST_VERSION_STATUS:-0}\"\n"
                + "fi\n"
                + "printf '%s\\n' \"$@\" > \"$TEST_TRACE\"\n"
                + "exit \"${TEST_STATUS:-0}\"\n");
        writeCommand(fakeJavaHome.resolve("bin/java"),
                "#!/usr/bin/env bash\n"
                + "printf '%s\\n' \"$@\" > \"$TEST_TRACE\"\n"
                + "exit \"${TEST_STATUS:-0}\"\n");
    }

    @Test
    void 테스트코드1_두셸의문법이유효하다() throws Exception {
        // given
        List<Path> scripts = Arrays.asList(verificationScript, launchScript);
        // when
        for (Path script : scripts) {
            Result result = run("-n", shellPath(script));
            // then
            assertEquals(0, result.status, result.output);
        }
        assertFalse(Files.exists(trace));
    }

    @Test
    void 테스트코드2_기본실행은외부연결없는단위검증을선택한다() throws Exception {
        // given
        overrides.put("TEST_JAVA_VERSION", "21.0.1");
        // when
        Result result = run(shellPath(verificationScript));
        // then
        assertEquals(0, result.status, result.output);
        assertEquals(Arrays.asList("-B", "-ntp", "-Pjava21", "test"), arguments());
    }

    @Test
    void 테스트코드3_인자의공백과셸기호를원문그대로전달한다() throws Exception {
        // given
        String argument = "-Dmobilecert.test.config=folder space/$(echo unused).xml";
        // when
        Result result = run(shellPath(verificationScript), "unit", argument);
        // then
        assertEquals(0, result.status, result.output);
        assertEquals(Arrays.asList("-B", "-ntp", "-Pjava21", argument, "test"), arguments());
    }

    @Test
    void 테스트코드4_디비와전체검증은명시적으로선택한다() throws Exception {
        // given
        String[] modes = {"db", "all", "coverage", "regression"};
        String[] profiles = {"java21,db", "java21,all-tests", "java21,coverage", "java21"};
        // when
        for (int i = 0; i < modes.length; i++) {
            Result result = run(shellPath(verificationScript), modes[i]);
            // then
            assertEquals(0, result.status, result.output);
            String goal = "coverage".equals(modes[i]) ? "verify" : "test";
            assertEquals(Arrays.asList("-B", "-ntp", "-P" + profiles[i], goal), arguments());
        }
    }

    @Test
    void 테스트코드5_자바팔의메이븐프로파일을선택한다() throws Exception {
        // given
        overrides.put("TEST_JAVA_VERSION", "1.8.0_402");
        // when
        Result result = run(shellPath(verificationScript), "unit");
        // then
        assertEquals(0, result.status, result.output);
        assertEquals(Arrays.asList("-B", "-ntp", "-Pjava8", "test"), arguments());
    }

    @Test
    void 테스트코드6_잘못된모드와지원하지않는자바를거부한다() throws Exception {
        // given
        overrides.put("TEST_JAVA_VERSION", "17.0.1");
        // when
        Result invalidMode = run(shellPath(verificationScript), "unknown");
        Result invalidJava = run(shellPath(verificationScript), "unit");
        // then
        assertEquals(2, invalidMode.status);
        assertEquals(2, invalidJava.status);
        assertFalse(Files.exists(trace));
    }

    @Test
    void 테스트코드7_메이븐실패코드를그대로반환한다() throws Exception {
        // given
        overrides.put("TEST_STATUS", "7");
        // when
        Result result = run(shellPath(verificationScript), "unit");
        // then
        assertEquals(7, result.status, result.output);
    }

    @Test
    void 테스트코드8_버전확인실패코드를그대로반환한다() throws Exception {
        // given
        overrides.put("TEST_VERSION_STATUS", "9");
        // when
        Result result = run(shellPath(verificationScript), "unit");
        // then
        assertEquals(9, result.status, result.output);
        assertFalse(Files.exists(trace));
    }

    @Test
    void 테스트코드9_빌드산출물이없으면자바를실행하지않는다() throws Exception {
        // given
        assertFalse(Files.exists(project.resolve("target/mobile-cert.jar")));
        // when
        Result result = run(shellPath(launchScript));
        // then
        assertEquals(1, result.status, result.output);
        assertFalse(Files.exists(trace));
    }

    @Test
    void 테스트코드10_실행셸은자바인자와종료코드를보존한다() throws Exception {
        // given
        Files.createDirectories(project.resolve("target/lib"));
        Files.createFile(project.resolve("target/mobile-cert.jar"));
        overrides.put("TEST_STATUS", "6");
        String option = "-Dexample=option with spaces";
        // when
        Result result = run(shellPath(launchScript), option);
        // then
        assertEquals(6, result.status, result.output);
        List<String> actual = arguments();
        assertEquals(4, actual.size());
        assertEquals(option, actual.get(0));
        assertEquals("-cp", actual.get(1));
        assertTrue(actual.get(2).contains("/target/mobile-cert.jar:"));
        assertTrue(actual.get(2).endsWith("/target/lib/*"));
        assertEquals("scbk.cert.server.ServiceLauncher", actual.get(3));
    }

    private List<String> arguments() throws IOException {
        return Files.readAllLines(trace, StandardCharsets.UTF_8);
    }

    private Result run(String... arguments) throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(bash);
        command.addAll(Arrays.asList(arguments));
        Path output = temporaryDirectory.resolve(UUID.randomUUID().toString() + ".txt");
        ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile());
        Map<String, String> environment = builder.environment();
        environment.put("PATH", fakeCommands.toString() + File.pathSeparator + environment.get("PATH"));
        environment.put("JAVA_HOME", shellPath(fakeJavaHome));
        environment.put("TEST_TRACE", shellPath(trace));
        environment.putAll(overrides);
        for (String name : Arrays.asList("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "BASH_ENV")) {
            environment.remove(name);
        }
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "셸 실행이 제한 시간을 넘었습니다.");
            return new Result(process.exitValue(),
                    new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private String findBash() throws Exception {
        String configured = System.getProperty("mobilecert.test.bash");
        if (configured == null || configured.isEmpty()) configured = System.getenv("MOBILECERT_TEST_BASH");
        List<String> candidates = configured == null || configured.isEmpty()
                ? Arrays.asList("C:/Program Files/Git/bin/bash.exe", "C:/Program Files/Git/usr/bin/bash.exe", "bash")
                : Arrays.asList(configured);
        for (String candidate : candidates) {
            Process process = null;
            try {
                ProcessBuilder builder = new ProcessBuilder(candidate, "-c", "exit 0");
                builder.environment().remove("BASH_ENV");
                builder.redirectErrorStream(true).redirectOutput(temporaryDirectory.resolve("bash-check.txt").toFile());
                process = builder.start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) return candidate;
            } catch (IOException unavailable) {
                // 다음 설치 위치를 확인한다. 지정한 경로는 다른 경로로 대체하지 않는다.
            } finally {
                if (process != null && process.isAlive()) process.destroyForcibly();
            }
        }
        return fail("Bash를 찾지 못했습니다. mobilecert.test.bash 또는 MOBILECERT_TEST_BASH에 실행 파일을 지정하세요.");
    }

    private static void writeCommand(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        assertTrue(file.toFile().setExecutable(true), "임시 명령에 실행 권한을 설정하지 못했습니다.");
    }

    private static String shellPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static final class Result {
        final int status;
        final String output;

        Result(int status, String output) {
            this.status = status;
            this.output = output;
        }
    }
}
