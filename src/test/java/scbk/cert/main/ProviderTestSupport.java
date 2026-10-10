package scbk.cert.main;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.conf.ConfigBean;
import scbk.cert.conf.KCBConfigBean;

/** Synthetic layouts copied from tools/tests/ProviderBaseline.java; no external configuration. */
abstract class ProviderTestSupport {
    static final Charset EUC_KR = Charset.forName("EUC-KR");
    private PrintStream previousOutput;
    private PrintStream discardedOutput;
    private String previousServerType;

    @BeforeEach
    void isolateLegacyOutputAndServerMode() {
        previousOutput = System.out;
        previousServerType = System.getProperty("scbk.cert.server.type");
        discardedOutput = new PrintStream(new OutputStream() {
            @Override public void write(int value) { }
            @Override public void write(byte[] bytes, int offset, int length) { }
        });
        System.setOut(discardedOutput);
        System.setProperty("scbk.cert.server.type", "synthetic");
    }

    @AfterEach
    void restoreOutputAndServerMode() {
        System.setOut(previousOutput);
        discardedOutput.close();
        if (previousServerType == null) {
            System.clearProperty("scbk.cert.server.type");
        } else {
            System.setProperty("scbk.cert.server.type", previousServerType);
        }
    }

    static byte[] kcbBody(String mode) {
        KCBHeaderBean header = new KCBHeaderBean();
        header.setTranCode("SYNTHETIC");
        header.setTranResponseCode("0000");
        byte[] body = spaces("CNSS".equals(mode) ? 997 : 281);
        put(body, 0, header.getTransaction());
        put(body, 130, mode);
        put(body, 176, "0000");
        put(body, 180, "\uD55C\uAE00 \uACB0\uACFC");
        put(body, body.length - 1, "Y");
        return body;
    }

    static byte[] lguRequest(String step) {
        byte[] request = spaces(284);
        put(request, 0, step);
        put(request, 42, "9001011000000");
        put(request, 83, "\uAC00\uB098\uB2E4");
        put(request, 104, "\uD55C\uAE00");
        return request;
    }

    static byte[] lguBody(String step) {
        byte[] response = spaces("STEP1".equals(step) ? 590 : 738);
        put(response, 0, "SYNTHETIC");
        put(response, 62, "0000");
        put(response, 66, "\uD55C\uAE00 \uC751\uB2F5");
        put(response, 290, step);
        return response;
    }

    static byte[] frame(boolean kcb, byte[] body) {
        byte[] prefix = String.format(Locale.ROOT, kcb ? "%04d" : "B%06d", body.length).getBytes(EUC_KR);
        byte[] result = Arrays.copyOf(prefix, prefix.length + body.length);
        System.arraycopy(body, 0, result, prefix.length, body.length);
        return result;
    }

    static String field(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, EUC_KR).trim();
    }

    static KCBConfigBean kcbConfig(LoopbackPeer peer, int timeoutMillis) {
        KCBConfigBean config = new KCBConfigBean();
        config.setKcbIp(peer.host());
        config.setKcbPort(Integer.toString(peer.port()));
        config.setKcbTimeout(Integer.toString(timeoutMillis));
        return config;
    }

    static ConfigBean lguConfig(LoopbackPeer peer) {
        return lguConfig(peer, 2000);
    }

    static ConfigBean lguConfig(LoopbackPeer peer, int timeoutMillis) {
        ConfigBean config = new ConfigBean();
        config.setLgdIp(peer.host());
        config.setLgdPort(Integer.toString(peer.port()));
        config.setLgdTimeout(Integer.toString(timeoutMillis));
        config.setMid("SYNTHETIC");
        config.setTxName("SYNTHETIC");
        return config;
    }

    private static byte[] spaces(int length) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) ' ');
        return bytes;
    }

    private static void put(byte[] bytes, int offset, String value) {
        byte[] encoded = value.getBytes(EUC_KR);
        System.arraycopy(encoded, 0, bytes, offset, encoded.length);
    }

    enum Reply { RESPONSE, DROP, STALL, OBSERVE_CLOSE }

    static final class Exchange {
        private final byte[] request;
        private final boolean clientClosed;

        Exchange(byte[] request, boolean clientClosed) {
            this.request = request;
            this.clientClosed = clientClosed;
        }

        byte[] request() { return request; }
        boolean clientClosed() { return clientClosed; }
    }

    /** A single ephemeral loopback exchange with bounded waits and a daemon peer. */
    static final class LoopbackPeer implements AutoCloseable {
        private final ServerSocket listener;
        private final CompletableFuture<Exchange> exchange = new CompletableFuture<>();
        private final CountDownLatch clientReturned = new CountDownLatch(1);
        private final Thread worker;
        private volatile Socket accepted;

        LoopbackPeer(int fixedRequestLength, byte[] response, Reply reply) throws IOException {
            listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            listener.setSoTimeout(3000);
            worker = new Thread(() -> {
                try (Socket peer = listener.accept()) {
                    accepted = peer;
                    peer.setSoTimeout(3000);
                    byte[] request;
                    if (fixedRequestLength > 0) {
                        request = readExactly(peer.getInputStream(), fixedRequestLength);
                    } else {
                        byte[] prefix = readExactly(peer.getInputStream(), 7);
                        if (prefix[0] != 'B') {
                            throw new IOException("Invalid synthetic request marker");
                        }
                        int bodyLength = Integer.parseInt(field(prefix, 1, 6));
                        if (bodyLength < 1 || bodyLength > 4096) {
                            throw new IOException("Synthetic request length outside test bound");
                        }
                        byte[] body = readExactly(peer.getInputStream(), bodyLength);
                        request = Arrays.copyOf(prefix, prefix.length + body.length);
                        System.arraycopy(body, 0, request, prefix.length, body.length);
                    }
                    boolean closed = false;
                    if (reply == Reply.RESPONSE || reply == Reply.OBSERVE_CLOSE) {
                        // Multiple writes exercise the real transport without timing assumptions.
                        for (int offset = 0; offset < response.length; offset += 17) {
                            peer.getOutputStream().write(response, offset, Math.min(17, response.length - offset));
                            peer.getOutputStream().flush();
                        }
                    }
                    if (reply == Reply.STALL || reply == Reply.OBSERVE_CLOSE) {
                        if (!clientReturned.await(3, TimeUnit.SECONDS)) {
                            throw new IOException("Synthetic client did not return within test bound");
                        }
                    }
                    if (reply == Reply.OBSERVE_CLOSE) {
                        peer.setSoTimeout(500);
                        try {
                            closed = peer.getInputStream().read() == -1;
                        } catch (SocketTimeoutException expectedWhenLeaked) {
                            closed = false;
                        }
                    }
                    exchange.complete(new Exchange(request, closed));
                } catch (Exception failure) {
                    exchange.completeExceptionally(failure);
                }
            }, "synthetic-provider-peer");
            worker.setDaemon(true);
            worker.start();
        }

        String host() {
            return listener.getInetAddress().getHostAddress();
        }

        int port() {
            return listener.getLocalPort();
        }

        Exchange awaitExchange() throws Exception {
            clientReturned.countDown();
            return exchange.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            clientReturned.countDown();
            try {
                listener.close();
            } finally {
                try {
                    Socket peer = accepted;
                    if (peer != null) {
                        peer.close();
                    }
                } finally {
                    worker.interrupt();
                    worker.join(2000);
                    if (worker.isAlive()) {
                        throw new AssertionError("Synthetic provider peer did not stop");
                    }
                }
            }
        }

        private static byte[] readExactly(InputStream input, int length) throws IOException {
            byte[] bytes = new byte[length];
            int offset = 0;
            while (offset < length) {
                int count = input.read(bytes, offset, length - offset);
                if (count == -1) throw new EOFException("Incomplete synthetic request");
                offset += count;
            }
            return bytes;
        }
    }
}
