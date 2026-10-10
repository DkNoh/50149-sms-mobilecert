package scbk.cert.main;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.util.Log;
import scbk.cert.util.LogCapture;

/** Synthetic loopback packets only; logging changes must not modify their bytes. */
@ResourceLock(Resources.SYSTEM_OUT)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
@ResourceLock("LOG_CONFIGURATION")
class ProviderLoggingTest extends ProviderTestSupport {
    private static final String PRIVATE_REQUEST = "SYN_REQUEST_PRIVATE";
    private static final String PRIVATE_RESPONSE = "SYN_RESPONSE_PRIVATE";
    private static final String PRIVATE_CI = "SYN_CI_PRIVATE_SENTINEL";
    private static final String PRIVATE_DI = "SYN_DI_PRIVATE_SENTINEL";
    private static final String PRIVATE_NAME = "\uD569\uC131\uAC1C\uC778\uC774\uB984";
    private static final String PRIVATE_PHONE = "00000000000";
    private static final String PRIVATE_OTP = "864209";
    private static final String PRIVATE_LEGACY_ID = "SYN_LEGACY_ID_PRIVATE";
    private static final String PRIVATE_EXCEPTION = "SYN_EXCEPTION_PRIVATE";

    @ParameterizedTest
    @ValueSource(strings = {"BSX1", "CNSS"})
    void normalExchangePreservesWireBytesAndLogsNoPrivateFields(String mode) throws Exception {
        byte[] request = privateRequest(mode);
        byte[] response = privateResponse(mode);
        byte[] expected = expectedResponse(response, mode);
        try (LogCapture capture = new LogCapture("log.day", "log.root");
             LoopbackPeer peer = new LoopbackPeer(request.length, frame(true, response), Reply.RESPONSE)) {
            String requestId = Log.beginRequest();
            try {
                byte[] actual = new KCBCommunication(kcbConfig(peer, 2000), PRIVATE_LEGACY_ID)
                        .socketComm(new String(request, EUC_KR), "SYN");
                assertArrayEquals(request, peer.awaitExchange().request(), "Logging must preserve outbound bytes");
                assertArrayEquals(expected, actual, "Logging must preserve the complete legacy response mapping");
                if ("CNSS".equals(mode)) {
                    assertEquals(PRIVATE_CI, field(actual, 281, 90));
                    assertEquals(PRIVATE_DI, field(actual, 371, 70));
                }

                List<LogEvent> events = capture.events();
                assertEquals(3, events.size(), "Expected bounded send/receive/parse diagnostic events");
                assertTrue(events.stream().allMatch(event -> event.getLevel() == Level.DEBUG),
                        "Successful provider detail must not generate INFO/WARN/ERROR events");
                assertTrue(events.stream().allMatch(event -> event.getThrown() == null));
                String text = capture.text();
                assertTrue(text.contains("event=KCB_REQUEST_SENT bytes=" + request.length));
                assertTrue(text.contains("event=KCB_RESPONSE_RECEIVED bytes=" + response.length));
                assertTrue(text.contains("event=KCB_RESPONSE_PARSED headerCode=0000 bodyCode=0000 elapsedMs="));
                assertTrue(text.contains("requestId=" + requestId));
                assertNoPrivateFields(text);
            } finally {
                Log.endRequest();
            }
        }
    }

    @Test
    void responseCodesWithControlCharactersAreRedactedWithoutChangingTheResponse() throws Exception {
        byte[] request = privateRequest("BSX1");
        byte[] response = privateResponse("BSX1");
        put(response, 25, "A\nB!");
        put(response, 176, "X\rY?");
        try (LogCapture capture = new LogCapture("log.day", "log.root");
             LoopbackPeer peer = new LoopbackPeer(request.length, frame(true, response), Reply.RESPONSE)) {
            byte[] actual = new KCBCommunication(kcbConfig(peer, 2000), PRIVATE_LEGACY_ID)
                    .socketComm(new String(request, EUC_KR), "SYN");
            assertArrayEquals(request, peer.awaitExchange().request());
            assertArrayEquals(response, actual, "Only log values are sanitized, never the protocol response");
            assertTrue(capture.text().contains("headerCode=INVALID bodyCode=INVALID"));
            assertFalse(capture.text().contains("A\nB!"));
            assertFalse(capture.text().contains("X\rY?"));
            assertTrue(capture.events().stream().allMatch(event -> event.getLevel() == Level.DEBUG));
            assertNoPrivateFields(capture.text());
        }
    }

    @Test
    void readTimeoutLogsSanitizedErrorWhilePreservingTheExistingFailureBehavior() throws Exception {
        byte[] request = privateRequest("BSX1");
        try (LogCapture capture = new LogCapture("log.day", "log.root");
             LoopbackPeer peer = new LoopbackPeer(request.length, new byte[0], Reply.STALL)) {
            Log.beginRequest();
            try {
                // Characterization only: fixing the pre-existing failure-frame NPE
                // belongs to a separate change, not to safe logging.
                assertThrows(NullPointerException.class, () ->
                        new KCBCommunication(kcbConfig(peer, 200), PRIVATE_LEGACY_ID)
                                .socketComm(new String(request, EUC_KR), "SYN"));
                assertArrayEquals(request, peer.awaitExchange().request());
                LogEvent error = assertCommunicationError(capture);
                assertTrue(error.getMessage().getFormattedMessage()
                        .contains("exceptionType=java.net.SocketTimeoutException"));
                assertFalse(capture.text().contains("Read timed out"), "Original exception text must not be logged");
                assertNoPrivateFields(capture.text());
            } finally {
                Log.endRequest();
            }
        }
    }

    @Test
    void exceptionContainingPrivateConfigurationIsNotAttachedOrRendered() {
        byte[] request = privateRequest("BSX1");
        KCBConfigBean configuration = new KCBConfigBean();
        configuration.setKcbIp("127.0.0.1");
        configuration.setKcbPort(PRIVATE_EXCEPTION); // Parsing fails before any connection is attempted.
        configuration.setKcbTimeout("200");
        try (LogCapture capture = new LogCapture("log.day", "log.root")) {
            assertThrows(NullPointerException.class, () ->
                    new KCBCommunication(configuration, PRIVATE_LEGACY_ID)
                            .socketComm(new String(request, EUC_KR), "SYN"));
            LogEvent error = assertCommunicationError(capture);
            assertTrue(error.getMessage().getFormattedMessage()
                    .contains("exceptionType=java.lang.NumberFormatException"));
            assertNoPrivateFields(capture.text());
        }
    }

    private static LogEvent assertCommunicationError(LogCapture capture) {
        List<LogEvent> errors = capture.events().stream()
                .filter(event -> event.getLevel() == Level.ERROR).collect(java.util.stream.Collectors.toList());
        assertEquals(1, errors.size());
        LogEvent error = errors.get(0);
        assertNotNull(error.getMessage());
        assertTrue(error.getMessage().getFormattedMessage()
                .startsWith("event=KCB_COMMUNICATION_FAILED outcome=UNKNOWN"));
        assertNull(error.getThrown(), "Raw Throwable must not reach the appender");
        assertNull(error.getThrownProxy(), "Raw Throwable proxy must not reach the appender");
        return error;
    }

    private static void assertNoPrivateFields(String text) {
        for (String sentinel : java.util.Arrays.asList(PRIVATE_REQUEST, PRIVATE_RESPONSE, PRIVATE_CI, PRIVATE_DI,
                PRIVATE_NAME, PRIVATE_PHONE, PRIVATE_OTP, PRIVATE_LEGACY_ID, PRIVATE_EXCEPTION)) {
            assertFalse(text.contains(sentinel), "A synthetic private field appeared in provider logs");
        }
    }

    private static byte[] privateRequest(String mode) {
        byte[] request = kcbBody(mode);
        put(request, 146, PRIVATE_REQUEST);
        put(request, 180, PRIVATE_OTP + " " + PRIVATE_PHONE + " " + PRIVATE_NAME);
        return request;
    }

    private static byte[] privateResponse(String mode) {
        byte[] response = kcbBody(mode);
        put(response, 180, PRIVATE_RESPONSE);
        if ("CNSS".equals(mode)) {
            put(response, 288, PRIVATE_DI);
            put(response, 448, PRIVATE_CI);
            put(response, 834, PRIVATE_PHONE);
            put(response, 866, PRIVATE_NAME);
        }
        return response;
    }

    /** Expected legacy 997-byte CNSS provider body maps to a 441-byte return body. */
    private static byte[] expectedResponse(byte[] response, String mode) {
        if (!"CNSS".equals(mode)) return response.clone();
        byte[] expected = new byte[441];
        Arrays.fill(expected, (byte) ' ');
        System.arraycopy(response, 0, expected, 0, 280);
        expected[280] = response[996];
        put(expected, 281, PRIVATE_CI);
        put(expected, 371, PRIVATE_DI);
        return expected;
    }

    private static void put(byte[] bytes, int offset, String value) {
        byte[] encoded = value.getBytes(EUC_KR);
        System.arraycopy(encoded, 0, bytes, offset, encoded.length);
    }
}
