package scbk.cert.main;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Desired behavior: current provider failure handling and socket cleanup fail these checks. */
@Tag("readiness")
@ResourceLock(Resources.SYSTEM_OUT)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ProviderReadinessTest extends ProviderTestSupport {
    @ParameterizedTest
    @Tag("inactive-lgu")
    @ValueSource(strings = {"STEP1", "STEP2"})
    void lguDroppedResponseMustReturnFailureCodeInsteadOfBlankFields(String step) throws Exception {
        try (LoopbackPeer peer = new LoopbackPeer(0, new byte[0], Reply.DROP)) {
            byte[] actual = new Communication(lguConfig(peer), "SYNTHETIC")
                    .socketComm(new String(lguRequest(step), EUC_KR));
            peer.awaitExchange();

            assertNotNull(actual);
            assertEquals("STEP1".equals(step) ? 590 : 738, actual.length);
            assertEquals("XXXX", field(actual, 62, 4), "EOF must be classified as a communication failure");
            assertEquals("XXXX", field(actual, 397, 4));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Reply.class, names = {"DROP", "STALL"})
    void kcbTransportFailureMustReturnFailureFrameInsteadOfThrowing(Reply reply) throws Exception {
        byte[] request = kcbBody("BSX1");
        try (LoopbackPeer peer = new LoopbackPeer(request.length, new byte[0], reply)) {
            byte[] actual = assertDoesNotThrow(() ->
                    new KCBCommunication(kcbConfig(peer, 200), "SYNTHETIC")
                            .socketComm(new String(request, EUC_KR), "SYN"),
                    "Dropped headers and read timeouts must yield a failure frame, not NullPointerException");
            peer.awaitExchange();

            assertNotNull(actual);
            assertEquals("XXXX", field(actual, 25, 4));
        }
    }

    @Test
    void kcbSocketMustCloseAfterSuccessfulExchange() throws Exception {
        assertProviderSocketCloses(true);
    }

    @Test
    @Tag("inactive-lgu")
    void lguSocketMustCloseAfterSuccessfulExchange() throws Exception {
        assertProviderSocketCloses(false);
    }

    private void assertProviderSocketCloses(boolean kcb) throws Exception {
        byte[] body = kcb ? kcbBody("BSX1") : lguBody("STEP1");
        try (LoopbackPeer peer = new LoopbackPeer(kcb ? body.length : 0,
                frame(kcb, body), Reply.OBSERVE_CLOSE)) {
            byte[] actual = kcb
                    ? new KCBCommunication(kcbConfig(peer, 2000), "SYNTHETIC")
                            .socketComm(new String(body, EUC_KR), "SYN")
                    : new Communication(lguConfig(peer), "SYNTHETIC")
                            .socketComm(new String(lguRequest("STEP1"), EUC_KR));

            assertNotNull(actual);
            assertTrue(peer.awaitExchange().clientClosed(),
                    "Provider connection must reach peer EOF within 500 ms after socketComm returns");
        }
    }
}
