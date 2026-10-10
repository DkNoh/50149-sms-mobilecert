package scbk.cert.baseline;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Arrays;

import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.mci.MCIHeaderBean;
import scbk.cert.bean.mci.MCIParser;
import scbk.cert.bean.mci.MCITransBean;
import scbk.cert.bean.uplus.HeaderBean;
import scbk.cert.bean.uplus.XPayAuthBean;
import scbk.cert.server.SocketManager;
import scbk.cert.util.CommonUtil;

/**
 * Dependency-free Java 8 characterization harness for fixed-width protocol code.
 * All values are synthetic; no provider, database, or network connection is used.
 */
public final class ProtocolBaseline {
    private static final Charset EUC_KR = Charset.forName("EUC-KR");
    private static int passed;

    private ProtocolBaseline() {
    }

    public static void main(String[] args) throws Exception {
        passed = 0;
        run("runtime.default-charset", new CheckedRunnable() {
            public void run() {
                assertEquals(System.getProperty("scbk.cert.test.defaultCharset", "EUC-KR"), Charset.defaultCharset().name());
            }
        });
        run("common.ascii-widths-and-spaces", new CheckedRunnable() {
            public void run() {
                testCommonAsciiWidthsAndSpaces();
            }
        });
        run("common.euc-kr-width-and-boundary", new CheckedRunnable() {
            public void run() {
                testCommonKoreanWidths();
            }
        });
        run("common.current-exceptions-and-lengths", new CheckedRunnable() {
            public void run() throws Exception {
                testCommonCurrentExceptionsAndLengths();
            }
        });
        run("mci.header-round-trip", new CheckedRunnable() {
            public void run() {
                testMciHeaderRoundTrip();
            }
        });
        run("mci.parser-segments-and-short-frame", new CheckedRunnable() {
            public void run() throws Exception {
                testMciParserSegmentsAndShortFrame();
            }
        });
        run("mci.response-frame", new CheckedRunnable() {
            public void run() {
                testMciResponseFrame();
            }
        });
        run("kcb.request-fixed-width", new CheckedRunnable() {
            public void run() {
                testKcbRequestFixedWidth();
            }
        });
        run("uplus.header-and-length-prefix", new CheckedRunnable() {
            public void run() {
                testUplusHeaderAndLengthPrefix();
            }
        });
        run("uplus.body-fixed-width", new CheckedRunnable() {
            public void run() {
                testUplusBodyFixedWidth();
            }
        });
        run("socket.recv-partial-and-eof", new CheckedRunnable() {
            public void run() throws Exception {
                testSocketRecvPartialAndEof();
            }
        });
        run("socket.recv-all-and-retry-limit", new CheckedRunnable() {
            public void run() throws Exception {
                testSocketRecvAllAndRetryLimit();
            }
        });
        run("socket.send-exact-bytes", new CheckedRunnable() {
            public void run() throws Exception {
                testSocketSendExactBytes();
            }
        });

        System.out.println("PASS total=" + passed
                + " java=" + System.getProperty("java.version")
                + " charset=" + Charset.defaultCharset().name());
    }

    private static void testCommonAsciiWidthsAndSpaces() {
        assertBytes(ascii("     "), bytes(CommonUtil.byteFiller("", 5)));
        assertBytes(ascii("ABCD "), bytes(CommonUtil.byteFiller("ABCD", 5)));
        assertBytes(ascii("ABCDE"), bytes(CommonUtil.byteFiller("ABCDE", 5)));
        assertBytes(ascii("ABCDE"), bytes(CommonUtil.byteFiller("ABCDEF", 5)));
        assertBytes(ascii(" AB   "), bytes(CommonUtil.byteFiller(" AB ", 6)));
        assertBytes(ascii("A    "), bytes(CommonUtil.byteFiller("A", 5)));
    }

    private static void testCommonKoreanWidths() {
        String korean = "\uAC00\uB098";
        assertBytes(hex("b0a1b3aa"), bytes(CommonUtil.byteFiller(korean, 4)));
        assertBytes(hex("b0a120"), bytes(CommonUtil.byteFiller("\uAC00", 3)));
        assertBytes(hex("b0a1"), bytes(CommonUtil.byteFiller(korean, 2)));

        // Java 8 EUC-KR replaces the split second character with '?'.
        assertBytes(hex("b0a13f"), bytes(CommonUtil.byteFiller(korean, 3)));
    }

    private static void testCommonCurrentExceptionsAndLengths() throws Exception {
        expectThrows(ArrayIndexOutOfBoundsException.class, new CheckedRunnable() {
            public void run() {
                CommonUtil.byteTrim(new byte[0], 3);
            }
        });
        expectThrows(StringIndexOutOfBoundsException.class, new CheckedRunnable() {
            public void run() {
                CommonUtil.toString(ascii("ABC"), 2, 2);
            }
        });
        expectThrows(NullPointerException.class, new CheckedRunnable() {
            public void run() {
                CommonUtil.byteFiller(null, 3);
            }
        });

        assertEquals(0, CommonUtil.parseInt("ABCDEF"));
        assertEquals(0, CommonUtil.parseInt("9999999999"));
        assertEquals(99999999, CommonUtil.parseInt("99999999"));
        assertEquals(999999, parseUplusLength(ascii("B999999")));
        assertEquals(0, parseUplusLength(ascii("B12X456")));
        assertEquals(9999, CommonUtil.parseInt(ascii("9999")));
    }

    private static void testMciHeaderRoundTrip() {
        MCIHeaderBean header = syntheticMciHeader();
        byte[] actual = bytes(header.getTransaction());
        byte[] expected = concat(
                fixed("N", 1),
                fixed("20260824", 8),
                fixed("123456", 6),
                fixed("AA", 2),
                fixed("SYN", 8),
                fixed("001", 3),
                fixed("ADDR", 16),
                fixed("I", 1),
                fixed("T", 1),
                fixed("Q", 1),
                fixed("S", 1),
                fixed("SYN-TX", 12),
                spaces(16),
                spaces(1),
                fixed("WEB", 3),
                ascii("07"),
                fixed("SESSION", 8));

        assertEquals(90, actual.length);
        assertBytes(expected, actual);
        MCIHeaderBean parsed = new MCIHeaderBean(actual);
        assertEquals("SYN-TX", parsed.getTranCd());
        assertEquals("ADDR", parsed.getIpv6Ard());
        assertEquals(7, parsed.getMciNdNo());
        assertEquals("SESSION", parsed.getMciSessIdNo());
    }

    private static void testMciParserSegmentsAndShortFrame() throws Exception {
        byte[] systemHeader = repeat((byte) 'H', 90);
        byte[] transactionHeader = repeat((byte) 'T', 60);
        byte[] frame = concat(systemHeader, transactionHeader, hex("1e"),
                ascii("CONV"), hex("1e"), ascii("DATA"), hex("1e"));
        MCIParser parser = new MCIParser(frame);

        assertBytes(systemHeader, parser.getSystemHeader());
        assertBytes(transactionHeader, parser.getTransactionHeader());
        assertEquals(9216, parser.getConv().length);
        assertEquals(9216, parser.getData().length);
        assertBytes(ascii("CONV"), Arrays.copyOf(parser.getConv(), 4));
        assertBytes(ascii("DATA"), Arrays.copyOf(parser.getData(), 4));
        assertAllZero(parser.getConv(), 4);
        assertAllZero(parser.getData(), 4);

        expectThrows(ArrayIndexOutOfBoundsException.class, new CheckedRunnable() {
            public void run() {
                new MCIParser(new byte[149]);
            }
        });
    }

    private static void testMciResponseFrame() {
        MCIHeaderBean header = syntheticMciHeader();
        MCITransBean transaction = new MCITransBean();
        transaction.setTmsgMsgTypCd("Q");
        transaction.setBlngBrNo(7);
        transaction.setEmpNo("SYN");
        transaction.setTxnBrNo(8);
        transaction.setServerId("S");
        transaction.setTermId("TERM");
        transaction.setCancelKey("N");

        byte[] response = new MCIParser().getResponse(
                header, transaction, "", "HD", "RD", "R0", "\uAC00\uB098");
        assertEquals(2048, response.length);
        assertBytes(ascii("00000258"), Arrays.copyOfRange(response, 0, 8));
        assertBytes(bytes(header.getTransaction()), Arrays.copyOfRange(response, 8, 98));
        assertBytes(bytes(transaction.getTransaction()), Arrays.copyOfRange(response, 98, 158));
        assertEquals(0x1e, response[158] & 0xff);
        assertBytes(ascii("R0"), Arrays.copyOfRange(response, 159, 161));
        assertBytes(hex("b0a1b3aa"), Arrays.copyOfRange(response, 161, 165));
        assertEquals(0x1f, response[265] & 0xff);
        assertAllZero(response, 266);
    }

    private static void testKcbRequestFixedWidth() {
        KCBCertReqBean request = new KCBCertReqBean();
        request.setMobileStep("SYN");
        request.setTid("SYN-TX");
        request.setMobileComm("X");
        request.setMertName("\uAC00\uB098");
        request.setBuyer(" A ");
        request.setIssuerCode("E");
        request.setIssuerMsg("\uD14C\uC2A4\uD2B8");

        byte[] actual = bytes(request.getTransaction());
        byte[] expected = concat(
                fixed("SYN", 5), fixed("SYN-TX", 24), fixed("X", 1),
                spaces(12), spaces(13), spaces(10), spaces(6), spaces(12),
                fixed("\uAC00\uB098", 20), spaces(1), fixed(" A ", 15), spaces(1),
                fixed("E", 4), fixed("\uD14C\uC2A4\uD2B8", 160));
        assertEquals(284, actual.length);
        assertBytes(expected, actual);
        assertEquals("e5cfbe2e0d57d26eff2ae6a596a449fab89aa09f292d4a7db7cbbcc8f9e5f1e5",
                sha256(actual));

        KCBCertReqBean parsed = new KCBCertReqBean(concat(spaces(226), actual));
        assertEquals("SYN", parsed.getMobileStep());
        assertEquals("SYN-TX", parsed.getTid());
        assertEquals("\uAC00\uB098", parsed.getMertName());
        assertEquals("A", parsed.getBuyer());
    }

    private static void testUplusHeaderAndLengthPrefix() {
        HeaderBean header = new HeaderBean();
        header.setMid("SYN-MID");
        header.setTxName("SYN-TX");
        header.setEncYn("N");
        header.setEncType("00");
        header.setSendDate("20260824123456");
        header.setRespCode("R000");
        header.setRespMsg("\uAC00\uB098");

        byte[] frame = bytes(header.getTransaction("\uD55C\uAE00"));
        byte[] expectedPayload = concat(
                fixed("SYN-MID", 20), fixed("SYN-TX", 25), fixed("N", 1),
                fixed("00", 2), fixed("20260824123456", 14), fixed("R000", 4),
                fixed("\uAC00\uB098", 160), hex("c7d1b1db"));
        assertEquals(230, expectedPayload.length);
        assertBytes(ascii("B000230"), Arrays.copyOf(frame, 7));
        assertBytes(expectedPayload, Arrays.copyOfRange(frame, 7, frame.length));
        assertEquals(230, parseUplusLength(Arrays.copyOf(frame, 7)));

        HeaderBean parsed = new HeaderBean(Arrays.copyOfRange(frame, 7, 233));
        assertEquals("SYN-MID", parsed.getMid());
        assertEquals("SYN-TX", parsed.getTxName());
        assertEquals("\uAC00\uB098", parsed.getRespMsg());
        assertBytes(ascii("B000226"), Arrays.copyOf(bytes(header.getTransaction("")), 7));
    }

    private static void testUplusBodyFixedWidth() {
        XPayAuthBean body = new XPayAuthBean();
        body.setMobileStep("SYN");
        body.setTid("SYN-TX");
        body.setMobileComm("X");
        body.setMertName("\uAC00\uB098");
        body.setBuyer(" A ");
        body.setIssuerCode("E");
        body.setIssuerMsg("\uD14C\uC2A4\uD2B8");
        body.setName("\uD55C\uAE00");
        body.setSitecode("SYN-SITE");

        byte[] actual = bytes(body.getTransaction());
        byte[] expected = concat(
                spaces(64), fixed("SYN", 5), fixed("SYN-TX", 24), fixed("X", 1),
                spaces(12), spaces(10), spaces(6), spaces(12), fixed("\uAC00\uB098", 20),
                spaces(1), fixed(" A ", 15), spaces(1), fixed("E", 4),
                fixed("\uD14C\uC2A4\uD2B8", 160), spaces(6), spaces(1),
                fixed("\uD55C\uAE00", 10), fixed("SYN-SITE", 12));
        assertEquals(364, actual.length);
        assertBytes(expected, actual);
        assertEquals("dcb414d59173acf4838fb4f862b180caf871ce0e265d567490acb8bef892e2a5",
                sha256(actual));
    }

    private static void testSocketRecvPartialAndEof() throws Exception {
        SocketManager partial = socketManager(new ChunkedInputStream(
                ascii("AB"), ascii("C"), ascii("DE")), new ByteArrayOutputStream());
        assertBytes(ascii("ABCDE"), partial.recv(5));

        SocketManager eof = socketManager(new ChunkedInputStream(
                ascii("AB"), ascii("C")), new ByteArrayOutputStream());
        assertBytes(concat(ascii("ABC"), new byte[2]), eof.recv(5));

        SocketManager immediateEof = socketManager(
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        assertBytes(new byte[3], immediateEof.recv(3));

        expectThrows(NegativeArraySizeException.class, new CheckedRunnable() {
            public void run() throws Exception {
                socketManager(new ByteArrayInputStream(new byte[0]),
                        new ByteArrayOutputStream()).recv(-1);
            }
        });
    }

    private static void testSocketRecvAllAndRetryLimit() throws Exception {
        byte[] firstFrame = ascii("B000003ABC");
        byte[] secondFrame = ascii("0003XYZ");
        SocketManager multiple = socketManager(new ChunkedInputStream(
                firstFrame, secondFrame), new ByteArrayOutputStream());
        assertBytes(concat(firstFrame, secondFrame), multiple.recvAll());

        final SocketManager zeros = socketManager(new ZeroInputStream(),
                new ByteArrayOutputStream());
        IOException error = expectThrows(IOException.class, new CheckedRunnable() {
            public void run() throws Exception {
                zeros.recvAll();
            }
        });
        assertEquals("inputstream-read-retry-count(5) exceed !", error.getMessage());
    }

    private static void testSocketSendExactBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SocketManager manager = socketManager(new ByteArrayInputStream(new byte[0]), output);
        byte[] frame = concat(ascii("B000004"), hex("b0a1b3aa"));
        manager.send(frame);
        assertBytes(frame, output.toByteArray());
    }

    private static MCIHeaderBean syntheticMciHeader() {
        MCIHeaderBean header = new MCIHeaderBean();
        header.setCrypDvCd("N");
        header.setTmsgWrtgDt("20260824");
        header.setTmsgWrtgTm("123456");
        header.setTrscGrcoCd("AA");
        header.setTmsgCreSysNm("SYN");
        header.setIssSrlNo("001");
        header.setIpv6Ard("ADDR");
        header.setInptDlvCd("I");
        header.setEnvrInfoDvCd("T");
        header.setRqstRspsDvCd("Q");
        header.setTrscSyncDvCd("S");
        header.setTranCd("SYN-TX");
        header.setChnlTypCd("WEB");
        header.setMciNdNo(7);
        header.setMciSessIdNo("SESSION");
        return header;
    }

    private static SocketManager socketManager(InputStream input, OutputStream output)
            throws IOException {
        SocketManager manager = new SocketManager();
        manager.setSocket(new FakeSocket(input, output));
        return manager;
    }

    private static int parseUplusLength(byte[] header) {
        return CommonUtil.parseInt(CommonUtil.toString(header, 1, 6));
    }

    private static byte[] fixed(String value, int width) {
        byte[] source = bytes(value);
        byte[] result = spaces(width);
        System.arraycopy(source, 0, result, 0, Math.min(source.length, width));
        return result;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(EUC_KR);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(Charset.forName("US-ASCII"));
    }

    private static byte[] spaces(int length) {
        return repeat((byte) ' ', length);
    }

    private static byte[] repeat(byte value, int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, value);
        return result;
    }

    private static byte[] concat(byte[]... values) {
        int length = 0;
        for (byte[] value : values) {
            length += value.length;
        }
        byte[] result = new byte[length];
        int position = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, position, value.length);
            position += value.length;
        }
        return result;
    }

    private static byte[] hex(String value) {
        if ((value.length() & 1) != 0) {
            throw new IllegalArgumentException("odd hex length");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value);
            StringBuilder result = new StringBuilder();
            for (byte item : hash) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void assertAllZero(byte[] value, int start) {
        for (int i = start; i < value.length; i++) {
            if (value[i] != 0) {
                throw new AssertionError("non-zero byte at " + i);
            }
        }
    }

    private static void assertBytes(byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError("byte mismatch expected=" + toHex(expected)
                    + " actual=" + toHex(actual));
        }
    }

    private static String toHex(byte[] value) {
        StringBuilder result = new StringBuilder();
        for (byte item : value) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected=" + expected + " actual=" + actual);
        }
    }

    private static <T extends Throwable> T expectThrows(Class<T> expected,
            CheckedRunnable action) throws Exception {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) {
                return expected.cast(actual);
            }
            throw new AssertionError("expected " + expected.getName() + " but got "
                    + actual.getClass().getName(), actual);
        }
        throw new AssertionError("expected " + expected.getName());
    }

    private static void run(String name, CheckedRunnable action) throws Exception {
        action.run();
        passed++;
        System.out.println("PASS " + name);
    }

    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class FakeSocket extends Socket {
        private final InputStream input;
        private final OutputStream output;

        private FakeSocket(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }
    }

    private static final class ChunkedInputStream extends InputStream {
        private final byte[][] chunks;
        private int chunkIndex;

        private ChunkedInputStream(byte[]... chunks) {
            this.chunks = chunks;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int count = read(single, 0, 1);
            return count < 0 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (chunkIndex >= chunks.length) {
                return -1;
            }
            byte[] chunk = chunks[chunkIndex++];
            if (chunk.length > length) {
                throw new AssertionError("test chunk exceeds requested read length");
            }
            System.arraycopy(chunk, 0, target, offset, chunk.length);
            return chunk.length;
        }

        @Override
        public int available() {
            int total = 0;
            for (int i = chunkIndex; i < chunks.length; i++) {
                total += chunks[i].length;
            }
            return total;
        }
    }

    private static final class ZeroInputStream extends InputStream {
        @Override
        public int read() {
            return 0;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            return 0;
        }

        @Override
        public int available() {
            return 1;
        }
    }
}
