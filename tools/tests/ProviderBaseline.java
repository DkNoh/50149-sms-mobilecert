package scbk.cert.baseline;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.conf.ConfigBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.main.Communication;
import scbk.cert.main.KCBCommunication;
import scbk.cert.server.SocketManager;

/** Synthetic provider responses exercise real communication classes without sockets. */
public final class ProviderBaseline {
    private static final Charset EUC_KR = Charset.forName("EUC-KR");
    private static final ByteArrayOutputStream snapshot = new ByteArrayOutputStream();

    public static void main(String[] args) throws Exception {
        PrintStream output = System.out;
        // The legacy LG U+ class prints raw packets; suppress even synthetic packets.
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
        try {
            kcb("BSX1");
            kcb("CNSS");
            uplus("STEP1", false);
            uplus("STEP2", false);
            uplus("STEP1", true);
            uplus("STEP2", true);
            Files.write(Paths.get(args[0]), snapshot.toByteArray());
        } finally { System.setOut(output); }
        System.out.println("PASS provider cases=6 (KCB BSX1/CNSS, LGU STEP1/STEP2 success/failure)");
    }

    private static void kcb(String mode) throws Exception {
        KCBConfigBean config = new KCBConfigBean();
        config.setKcbIp("synthetic");
        config.setKcbPort("0");
        config.setKcbTimeout("1");
        KCBHeaderBean header = new KCBHeaderBean();
        header.setTranCode("SYNTHETIC");
        header.setTranResponseCode("0000");
        byte[] body = spaces(mode.equals("CNSS") ? 997 : 281);
        put(body, 0, header.getTransaction());
        put(body, 130, mode);
        put(body, 176, "0000");
        put(body, 180, "\uD55C\uAE00 \uACB0\uACFC");
        put(body, mode.equals("CNSS") ? 996 : 280, "Y");
        SocketManager.response = concat(String.format("%04d", body.length).getBytes(EUC_KR), body);
        SocketManager.fail = false;
        String request = new String(body, EUC_KR);
        byte[] actual = new KCBCommunication(config, "SYNTHETIC").socketComm(request, "SYN");
        check(Arrays.equals(body, SocketManager.sent), "KCB request bytes");
        check(new String(actual, 180, 100, EUC_KR).trim().equals("\uD55C\uAE00 \uACB0\uACFC"), "KCB Korean response");
        record(SocketManager.sent);
        record(actual);
    }

    private static void uplus(String step, boolean fail) throws Exception {
        ConfigBean config = new ConfigBean();
        config.setLgdIp("synthetic");
        config.setLgdPort("0");
        config.setLgdTimeout("1");
        config.setMid("SYNTHETIC");
        config.setTxName("SYNTHETIC");
        byte[] request = spaces(284);
        put(request, 0, step);
        put(request, 42, "9001011000000");
        put(request, 83, "\uAC00\uB098\uB2E4");
        put(request, 104, "\uD55C\uAE00");
        byte[] response = spaces(step.equals("STEP1") ? 590 : 738);
        put(response, 0, "SYNTHETIC");
        put(response, 62, "0000");
        put(response, 66, "\uD55C\uAE00 \uC751\uB2F5");
        put(response, 290, step);
        SocketManager.response = concat(String.format("B%06d", response.length).getBytes(EUC_KR), response);
        SocketManager.fail = fail;
        System.setProperty("scbk.cert.server.type", "synthetic");
        byte[] actual = new Communication(config, "SYNTHETIC").socketComm(new String(request, EUC_KR));
        check(actual != null, "LGU response exists");
        if (!fail) {
            byte[] sent = SocketManager.sent.clone();
            int declared = Integer.parseInt(new String(sent, 1, 6, EUC_KR));
            check(declared == sent.length - 7, "LGU byte length prefix");
            // Only normalize the clock-generated request timestamp, never payload bytes.
            Arrays.fill(sent, 7 + 48, 7 + 62, (byte) '0');
            record(sent);
        } else {
            check(new String(actual, 62, 4, EUC_KR).equals("XXXX"), "LGU existing failure code");
            Arrays.fill(actual, 48, 62, (byte) '0');
        }
        record(actual);
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
    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    private static void record(byte[] bytes) throws Exception {
        snapshot.write(Integer.toString(bytes.length).getBytes(EUC_KR));
        snapshot.write(':');
        snapshot.write(bytes);
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
