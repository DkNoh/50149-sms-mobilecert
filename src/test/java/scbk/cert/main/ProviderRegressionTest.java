package scbk.cert.main;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ResourceLock(Resources.SYSTEM_OUT)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ProviderRegressionTest extends ProviderTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"BSX1", "CNSS"})
    void kcbSuccessPreservesRequestAndParsesResponse(String mode) throws Exception {
        byte[] body = kcbBody(mode);
        try (LoopbackPeer peer = new LoopbackPeer(body.length, frame(true, body), Reply.RESPONSE)) {
            byte[] actual = new KCBCommunication(kcbConfig(peer, 2000), "SYNTHETIC")
                    .socketComm(new String(body, EUC_KR), "SYN");

            assertArrayEquals(body, peer.awaitExchange().request());
            assertNotNull(actual);
            assertEquals("CNSS".equals(mode) ? 441 : 281, actual.length);
            assertEquals("0000", field(actual, 25, 4));
            assertEquals(mode, field(actual, 130, 4));
            assertEquals("0000", field(actual, 176, 4));
            assertEquals("\uD55C\uAE00 \uACB0\uACFC", field(actual, 180, 100));
            assertEquals("Y", field(actual, 280, 1));
        }
    }

    @ParameterizedTest
    @Tag("inactive-lgu")
    @ValueSource(strings = {"STEP1", "STEP2"})
    void lguSuccessPreservesResponseAndSendsByteLengthPrefix(String step) throws Exception {
        byte[] response = lguBody(step);
        try (LoopbackPeer peer = new LoopbackPeer(0, frame(false, response), Reply.RESPONSE)) {
            byte[] actual = new Communication(lguConfig(peer), "SYNTHETIC")
                    .socketComm(new String(lguRequest(step), EUC_KR));
            byte[] sent = peer.awaitExchange().request();

            assertArrayEquals(response, actual);
            assertEquals('B', sent[0]);
            assertEquals(sent.length - 7, Integer.parseInt(field(sent, 1, 6)));
            assertEquals(response.length + 7, sent.length);
            assertEquals(step, field(sent, 7 + 290, 5));
            assertEquals("\uAC00\uB098\uB2E4", field(sent, 7 + 360, 20));
            assertEquals("\uD55C\uAE00", field(sent, 7 + 381, 15));
        }
    }

    @ParameterizedTest
    @Tag("inactive-lgu")
    @ValueSource(strings = {"STEP1", "STEP2"})
    void lguReadTimeoutReturnsFailureCode(String step) throws Exception {
        try (LoopbackPeer peer = new LoopbackPeer(0, new byte[0], Reply.STALL)) {
            byte[] actual = new Communication(lguConfig(peer, 200), "SYNTHETIC")
                    .socketComm(new String(lguRequest(step), EUC_KR));
            peer.awaitExchange();

            assertNotNull(actual);
            assertEquals("STEP1".equals(step) ? 590 : 738, actual.length);
            assertEquals("XXXX", field(actual, 62, 4));
            assertEquals("XXXX", field(actual, 397, 4));
        }
    }
}
