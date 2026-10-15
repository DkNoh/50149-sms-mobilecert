package scbk.cert.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock("LOG_CONFIGURATION")
@ResourceLock(Resources.SYSTEM_ERR)
class LogSafetyTest {
    private static final String CATEGORY = "log.synthetic.safety";

    @AfterEach
    void 요청문맥을정리한다() {
        Log.endRequest();
    }

    @Test
    void 테스트코드1_명시한로그수준과기존문구를_유지한다() {
        // given
        try (LogCapture capture = new LogCapture(CATEGORY)) {
            // when
            Log.debug(CATEGORY, "event=DETAIL", this);
            Log.info(CATEGORY, "event=COMPLETE", this);
            Log.warn(CATEGORY, "event=CONTROLLED_FAILURE", this);
            Log.error(CATEGORY, "event=SYSTEM_FAILURE", this);
            Log.write(CATEGORY, "legacy-exact-text", this);

            // then
            assertEquals(java.util.Arrays.asList(Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.ERROR),
                    capture.events().stream().map(event -> event.getLevel()).collect(java.util.stream.Collectors.toList()));
            assertEquals("legacy-exact-text", capture.events().get(4).getMessage().getFormattedMessage());
        }
    }

    @Test
    void 테스트코드2_예외의비밀값과파일이름을_출력하지않는다() {
        // given
        SQLException failure = new SQLException("SYNTHETIC_PASSWORD jdbc:secret SQL_BINDING_SECRET",
                "42000", 942);
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("scbk.cert.dao.SyntheticDriver", "execute", "PRIVATE_FILE_SECRET", 7)
        });
        failure.initCause(new IllegalStateException("CAUSE_SECRET"));
        failure.addSuppressed(new IllegalArgumentException("SUPPRESSED_SECRET"));
        failure.setNextException(new SQLException("NEXT_SQL_SECRET", "08006", 17002));
        try (LogCapture capture = new LogCapture(CATEGORY)) {
            // when
            Log.error(CATEGORY, "event=DB_WRITE_FAILED", failure, this);
            String output = capture.text();

            // then
            assertEquals(1, capture.events().size());
            assertEquals(Level.ERROR, capture.events().get(0).getLevel());
            assertNull(capture.events().get(0).getThrown());
            for (String secret : java.util.Arrays.asList("SYNTHETIC_PASSWORD", "jdbc:secret", "SQL_BINDING_SECRET",
                    "PRIVATE_FILE_SECRET", "CAUSE_SECRET", "SUPPRESSED_SECRET", "NEXT_SQL_SECRET")) {
                assertFalse(output.contains(secret), "진단 메시지에 테스트용 비밀값을 포함하면 안 된다.");
            }
            assertTrue(output.contains("exceptionType=java.sql.SQLException"));
            assertTrue(output.contains("sqlState=42000"));
            assertTrue(output.contains("vendorCode=942"));
            assertTrue(output.contains("17002"));
            assertTrue(output.contains("scbk.cert.dao.SyntheticDriver.execute:7"));
        }
    }

    @Test
    void 테스트코드3_로그가비활성화되면_예외정보를읽지않는다() {
        // given
        SQLException dangerous = new SQLException("SYNTHETIC_SECRET") {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new AssertionError("비활성화된 로그의 진단 정보를 만들면 안 된다.");
            }
        };
        try (LogCapture capture = new LogCapture(CATEGORY)) {
            capture.setLevel(CATEGORY, Level.OFF);

            // when
            assertDoesNotThrow(() -> Log.error(CATEGORY, "event=DISABLED", dangerous, this));

            // then
            assertTrue(capture.events().isEmpty());
        }
    }

    @Test
    void 테스트코드4_예상하지못한상태코드를_차단한다() {
        // given
        List<String> invalidCodes = java.util.Arrays.asList("123456", "SYNTHETIC_PRIVATE", "A\nB", "lowercase", "");
        SQLException failure = new SQLException("SYNTHETIC_SECRET", "STATE_SECRET", 1);
        try (LogCapture capture = new LogCapture(CATEGORY)) {
            // when
            String normalProviderCode = Log.code("B000");
            String normalResponseCode = Log.code("0000");
            List<String> invalidResults = new ArrayList<>();
            for (String invalid : invalidCodes) {
                invalidResults.add(Log.code(invalid));
            }
            String nullResult = Log.code(null);
            Log.warn(CATEGORY, "event=SQL_FAILURE", failure, this);

            // then
            assertEquals("B000", normalProviderCode);
            assertEquals("0000", normalResponseCode);
            for (String result : invalidResults) {
                assertEquals("INVALID", result);
            }
            assertEquals("INVALID", nullResult);
            assertEquals(Level.WARN, capture.events().get(0).getLevel());
            assertTrue(capture.text().contains("sqlState=UNKNOWN"));
            assertFalse(capture.text().contains("SECRET"));
        }
    }

    @Test
    void 테스트코드5_로그문구를_제한된길이의한줄로출력한다() {
        // given
        String message = "event=SAFE\r\n\t\u2028\u2029" + String.join("", java.util.Collections.nCopies(1024, "x"));
        try (LogCapture capture = new LogCapture(CATEGORY)) {
            // when
            Log.info(CATEGORY, message, this);
            String output = capture.events().get(0).getMessage().getFormattedMessage();

            // then
            assertEquals(512, output.length());
            for (String control : java.util.Arrays.asList("\r", "\n", "\t", "\u2028", "\u2029")) {
                assertFalse(output.contains(control));
            }
        }
    }

    @Test
    @Timeout(15)
    void 테스트코드6_동시요청의식별자를_분리하고문맥을정리한다() throws Exception {
        // given
        try (LogCapture capture = new LogCapture(CATEGORY)) {
            ExecutorService workers = Executors.newFixedThreadPool(8);
            try {
                List<Future<String>> results = new ArrayList<>();
                Set<String> ids = new HashSet<>();

                // when
                for (int index = 0; index < 64; index++) {
                    results.add(workers.submit(() -> {
                        String id = Log.beginRequest();
                        try {
                            Log.info(CATEGORY, "event=REQUEST", this);
                        } finally {
                            Log.endRequest();
                        }
                        Log.info(CATEGORY, "event=AFTER_REQUEST", this);
                        return id;
                    }));
                }
                for (Future<String> result : results) {
                    ids.add(result.get(5, TimeUnit.SECONDS));
                }

                // then
                assertEquals(64, ids.size());
                assertEquals(128, capture.events().size());
                for (String id : ids) {
                    assertTrue(capture.events().stream().anyMatch(event -> event.getMessage()
                            .getFormattedMessage().equals("event=REQUEST requestId=" + id)));
                }
                assertEquals(64, capture.events().stream().filter(event -> event.getMessage()
                        .getFormattedMessage().equals("event=AFTER_REQUEST")).count());
            } finally {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    @Timeout(3)
    void 테스트코드7_순환하는예외를_비밀문구없이제한된길이로출력한다() {
        // given
        SQLException first = new SQLException("FIRST_SECRET", "42000", 1) {
            @Override
            public String getMessage() {
                throw new AssertionError("예외 메시지를 읽으면 안 된다.");
            }
        };
        IllegalArgumentException second = new IllegalArgumentException("SECOND_SECRET", first);
        first.initCause(second);
        first.addSuppressed(second);
        try (LogCapture capture = new LogCapture(CATEGORY)) {
            // when
            assertDoesNotThrow(() -> Log.error(CATEGORY, "event=CYCLE", first, this));

            // then
            assertEquals(1, capture.events().size());
            assertFalse(capture.text().contains("SECRET"));
            assertTrue(capture.text().length() < 12000);
        }
    }

    @Test
    void 테스트코드8_로그진단정보생성이실패해도_호출흐름을유지한다() throws Exception {
        // given
        RuntimeException unavailableMetadata = new RuntimeException("PRIVATE_EXCEPTION_TEXT") {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new IllegalStateException("PRIVATE_DIAGNOSTIC_FAILURE");
            }
        };
        PrintStream previous = System.err;
        ByteArrayOutputStream diagnosticOutput = new ByteArrayOutputStream();
        try (PrintStream diagnostics = new PrintStream(diagnosticOutput, true, StandardCharsets.UTF_8.name());
             LogCapture capture = new LogCapture(CATEGORY)) {
            System.setErr(diagnostics);

            // when
            assertDoesNotThrow(() -> Log.error(CATEGORY, "event=UNAVAILABLE_METADATA", unavailableMetadata, this));
            Log.info(CATEGORY, "event=CALLER_CONTINUED", this);

            // then
            assertEquals(1, capture.events().size());
            assertEquals(Level.INFO, capture.events().get(0).getLevel());
            assertTrue(capture.text().contains("event=CALLER_CONTINUED"));
            assertFalse(diagnosticOutput.toString(StandardCharsets.UTF_8.name()).contains("PRIVATE"));
        } finally {
            System.setErr(previous);
        }
    }
}
