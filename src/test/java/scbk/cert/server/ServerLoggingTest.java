package scbk.cert.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import scbk.cert.conf.ConfigBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.util.Log;
import scbk.cert.util.LogCapture;
import scbk.cert.util.XMLFactory;

/** Request boundary checks with finite memory streams and no provider/database configuration. */
@Timeout(10)
@ResourceLock("LOG_CONFIGURATION")
class ServerLoggingTest {
    private static final String SECRET = "SYNTHETIC_PRIVATE_EXCEPTION_TEXT";
    private static final Pattern REQUEST_ID = Pattern.compile("(?:^|\\s)requestId=([^\\s]+)");
    private Field configurationField;
    private Object previousConfiguration;

    @BeforeEach
    void useIsolatedInMemoryConfiguration() throws Exception {
        configurationField = XMLFactory.class.getDeclaredField("fMap");
        configurationField.setAccessible(true);
        previousConfiguration = configurationField.get(null);
        ConfigBean configuration = new ConfigBean();
        configuration.setServerTimeout("1250");
        HashMap<String, Object> entities = new HashMap<>();
        entities.put("XPAY_LEASED", configuration);
        entities.put("MOBILE_CERT", new KCBConfigBean());
        configurationField.set(null, entities);
    }

    @AfterEach
    void restoreConfigurationAndRequestScope() throws Exception {
        try {
            configurationField.set(null, previousConfiguration);
        } finally {
            Log.endRequest();
        }
    }

    @Test
    void emptyInputIsDebugWithoutClaimingAckAndRequestIdsDoNotLeakAcrossCalls() {
        try (LogCapture capture = new LogCapture("log.day", "log.root")) {
            Server server = new Server();
            MemorySocket first = new MemorySocket(new ByteArrayInputStream(new byte[0]));
            MemorySocket second = new MemorySocket(new ByteArrayInputStream(new byte[0]));

            server.serve(first);
            server.serve(second);
            Log.info("log.day", "event=test.afterRequests", this);

            List<LogEvent> ended = events(capture, "event=mci.inputEndedOrIncomplete");
            assertEquals(2, ended.size());
            assertEquals(Level.DEBUG, ended.get(0).getLevel());
            assertEquals(Level.DEBUG, ended.get(1).getLevel());
            assertNotEquals(requestId(ended.get(0)), requestId(ended.get(1)));
            assertFalse(message(events(capture, "event=test.afterRequests").get(0)).contains("requestId="));
            assertFalse(capture.text().contains("ACK"));
            assertTrue(capture.events().stream().noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.WARN)));
            assertTrue(first.closed && second.closed);
            assertEquals(1250, first.timeout);
            assertEquals(1250, second.timeout);
            assertEquals(0, first.output.size());
            assertEquals(0, second.output.size());
        }
    }

    @Test
    void inputTimeoutIsWarnAndPreservesCleanupWithoutExceptionMessage() {
        InputStream timeoutInput = new InputStream() {
            @Override public int read() throws IOException {
                throw new SocketTimeoutException(SECRET + "\nforged-log-line");
            }
        };
        MemorySocket socket = new MemorySocket(timeoutInput);
        try (LogCapture capture = new LogCapture("log.day", "log.root")) {
            new Server().serve(socket);
            Log.info("log.day", "event=test.afterTimeout", this);

            List<LogEvent> timeouts = events(capture, "event=mci.inputTimeout");
            assertEquals(1, timeouts.size());
            assertEquals(Level.WARN, timeouts.get(0).getLevel());
            assertTrue(message(timeouts.get(0)).contains("exceptionType=java.net.SocketTimeoutException"));
            assertFalse(capture.text().contains(SECRET));
            assertFalse(capture.text().contains("forged-log-line"));
            assertFalse(capture.text().contains("ACK"));
            assertFalse(message(events(capture, "event=test.afterTimeout").get(0)).contains("requestId="));
            assertTrue(socket.closed);
            assertEquals(0, socket.output.size());
        }
    }

    @Test
    void inputReadFailureIsWarnAndPreservesCleanupWithoutExceptionMessage() {
        InputStream failedInput = new InputStream() {
            @Override public int read() throws IOException {
                throw new IOException(SECRET + "\nforged-log-line");
            }
        };
        assertInputFailureIsLoggedAndClosed(failedInput, Level.WARN,
                "event=mci.inputReadFailed", "java.io.IOException");
    }

    @Test
    void unexpectedInputFailureIsErrorAndPreservesCleanupWithoutExceptionMessage() {
        InputStream failedInput = new InputStream() {
            @Override public int read() {
                throw new IllegalStateException(SECRET + "\nforged-log-line");
            }
        };
        assertInputFailureIsLoggedAndClosed(failedInput, Level.ERROR,
                "event=mci.inputFailed", "java.lang.IllegalStateException");
    }

    private void assertInputFailureIsLoggedAndClosed(InputStream input, Level expectedLevel,
            String expectedEvent, String expectedExceptionType) {
        MemorySocket socket = new MemorySocket(input);
        try (LogCapture capture = new LogCapture("log.day", "log.root")) {
            new Server().serve(socket);
            Log.info("log.day", "event=test.afterInputFailure", this);

            List<LogEvent> failures = events(capture, expectedEvent);
            assertEquals(1, failures.size());
            assertEquals(expectedLevel, failures.get(0).getLevel());
            assertTrue(message(failures.get(0)).contains("exceptionType=" + expectedExceptionType));
            requestId(failures.get(0));
            assertFalse(capture.text().contains(SECRET));
            assertFalse(capture.text().contains("forged-log-line"));
            assertFalse(capture.text().contains("ACK"));
            assertFalse(message(events(capture, "event=test.afterInputFailure").get(0)).contains("requestId="));
            assertTrue(socket.closed);
            assertEquals(0, socket.output.size());
        }
    }

    @Test
    void requestAndCloseFailuresShareOneIdAndFinallyClearsIt() {
        MemorySocket socket = new MemorySocket(new ByteArrayInputStream(new byte[0]));
        socket.failInput = true;
        socket.failClose = true;
        try (LogCapture capture = new LogCapture("log.day", "log.root")) {
            new Server().serve(socket);
            Log.info("log.day", "event=test.afterFailure", this);

            List<LogEvent> requestFailures = events(capture, "event=server.requestFailed");
            List<LogEvent> closeFailures = events(capture, "event=server.socketCloseFailed");
            assertEquals(1, requestFailures.size());
            assertEquals(1, closeFailures.size());
            assertEquals(Level.ERROR, requestFailures.get(0).getLevel());
            assertEquals(Level.ERROR, closeFailures.get(0).getLevel());
            assertEquals(requestId(requestFailures.get(0)), requestId(closeFailures.get(0)));
            assertFalse(message(events(capture, "event=test.afterFailure").get(0)).contains("requestId="));
            assertFalse(capture.text().contains(SECRET));
            assertTrue(socket.closed, "The original close attempt must still occur after initialization fails");
            assertEquals(0, socket.output.size());
        }
    }

    private static List<LogEvent> events(LogCapture capture, String eventName) {
        return capture.events().stream().filter(event -> message(event).startsWith(eventName)).collect(java.util.stream.Collectors.toList());
    }

    private static String message(LogEvent event) {
        return event.getMessage().getFormattedMessage();
    }

    private static String requestId(LogEvent event) {
        Matcher match = REQUEST_ID.matcher(message(event));
        assertTrue(match.find(), "Request events must carry a correlation ID");
        return match.group(1);
    }

    private static final class MemorySocket extends Socket {
        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private boolean failInput;
        private boolean failClose;
        private boolean closed;
        private int timeout;

        private MemorySocket(InputStream input) { this.input = input; }

        @Override public InputStream getInputStream() throws IOException {
            if (failInput) throw new IOException(SECRET);
            return input;
        }

        @Override public OutputStream getOutputStream() { return output; }

        @Override public void setSoTimeout(int timeout) { this.timeout = timeout; }

        @Override public void close() throws IOException {
            closed = true;
            if (failClose) throw new IOException(SECRET);
        }
    }
}
