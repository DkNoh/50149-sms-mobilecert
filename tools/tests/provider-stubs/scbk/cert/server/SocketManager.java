package scbk.cert.server;

import java.io.IOException;
import java.util.Arrays;

/** Compiled only by compare-runtimes.py; no java.net classes are used. */
public final class SocketManager {
    public static byte[] response;
    public static byte[] sent;
    public static boolean fail;
    private int offset;

    public SocketManager(String host, int port) {
        if (!"synthetic".equals(host) || port != 0) throw new AssertionError("Unexpected test destination");
    }
    public void setSocketTimeout(int connect, int read) { }
    public void connect() throws IOException {
        if (fail) throw new IOException("Synthetic connection failure");
    }
    public void send(byte[] bytes) { sent = bytes.clone(); }
    public byte[] recv(int length) {
        if (offset + length > response.length) throw new AssertionError("Unexpected response length");
        byte[] result = Arrays.copyOfRange(response, offset, offset + length);
        offset += length;
        return result;
    }
}
