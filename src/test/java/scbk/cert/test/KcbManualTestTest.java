package scbk.cert.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.DataInputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.mci.MCIHeaderBean;
import scbk.cert.bean.mci.MCIParser;
import scbk.cert.bean.mci.MCITransBean;
import scbk.cert.bean.uplus.XPayAuthBean;
import scbk.cert.util.CertCharset;
import scbk.cert.util.CommonUtil;

/** Synthetic parser/serializer checks and loopback-only socket coverage. */
public final class KcbManualTestTest {
    @TempDir Path temporary;

    private static final String TID = "CERT20260101000000000001";

    @Test
    void 테스트1_송신옵션과도움말을검증한다() throws Exception {
        // given
        String[][] invalid = { {}, {"--port", "12345"}, {"--send"}, {"--help", "--send"},
                {"--send", "--port", "0"}, {"--send", "--port", "65536"},
                {"--send", "--port", "12345", "--host", "example.invalid"},
                {"--send", "--port", "12345", "--phone", "PRIVATE"},
                {"--send", "--port", "12345", "--timeout-ms", "999"},
                {"--send", "--port", "12345", "--timeout-ms", "120001"},
                {"--send", "--port", "12345", "--send"},
                {"--send", "--port", "12345", "--charset", "UTF-8"}};
        // when
        KcbManualTest.Options valid = options();
        // then
        ok(KcbManualTest.Options.parse(new String[] {"--help"}).help);
        ok(KcbManualTest.Options.parse(new String[] {"--check", "--port", "12345"}).check);
        expect(KcbManualTest.InvalidInput.class,
                () -> KcbManualTest.Options.parse(new String[] {"--check", "--send", "--port", "12345"}));
        eq(12345, valid.port);
        eq(30000, valid.timeoutMs);
        for (String[] value : invalid) expect(KcbManualTest.InvalidInput.class,
                () -> KcbManualTest.Options.parse(value));
    }

    @Test
    void 테스트2_기존파서와요청바이트를대조한다() {
        // given
        KcbManualTest.Input input = input();
        // when
        byte[] first = KcbManualTest.request(input, "STEP1", "", "");
        byte[] second = KcbManualTest.request(input, "STEP2", TID, "000000");
        MCIParser parser = new MCIParser(Arrays.copyOfRange(first, 8, first.length));
        KCBCertReqBean parsed = new KCBCertReqBean(CommonUtil.setFiller(226)
                + new String(parser.getData(), 188, 284, CertCharset.EUC_KR));
        // then
        eq(633, first.length);
        eq("00000625", ascii(first, 0, 8));
        eq((byte) 0x1e, first[158]);
        eq((byte) 0x1e, first[159]);
        eq((byte) 0x1e, first[632]);
        eq("SYN-KCB", new MCIHeaderBean(parser.getSystemHeader()).getTranCd());
        eq("STEP1", parsed.getMobileStep());
        eq(input.name, parsed.getBuyer());
        eq(input.birthAndSex, parsed.getMobileSsn());
        eq(input.phone, parsed.getMobileNum());
        eq("STEP2", ascii(second, 348, 5));
        eq(TID, ascii(second, 353, 24));
        eq("000000", ascii(second, 413, 6));
        for (int i = 397; i < 403; i++) eq((byte) ' ', first[i]);
        byte[] korean = KcbManualTest.encode(input.name);
        ok(Arrays.equals(korean, Arrays.copyOfRange(first, 452, 452 + korean.length)));
    }

    @Test
    void 테스트3_기존응답생성기와양단계를대조한다() {
        // given
        KcbManualTest.Input input = input();
        // when
        byte[] first = reply(input, "STEP1", TID, "0000", true);
        byte[] second = reply(input, "STEP2", TID, "0000", true);
        // then
        eq(2048, first.length);
        eq(728, KcbManualTest.responseLength(first));
        eq(888, KcbManualTest.responseLength(second));
        eq("P", KcbManualTest.response(first, input, "STEP1", "").certMode);
        eq(TID, KcbManualTest.response(second, input, "STEP2", TID).tid);
    }

    @Test
    void 테스트4_민감입력과바이트초과를거부한다() throws Exception {
        // given
        String[] invalidNames = {"", " leading", "trailing ", "A\nB", "A\u001eB", "\uD83D\uDE00", "가나다라마바사아"};
        // when / then
        for (String name : invalidNames) expect(KcbManualTest.InvalidInput.class,
                () -> inputWith("SYN", "01000000000", "0001013", name));
        expect(KcbManualTest.InvalidInput.class, () -> inputWith("A'OR'1", "01000000000", "0001013", "가나"));
        expect(KcbManualTest.InvalidInput.class, () -> inputWith("SYN", "010-0000-0000", "0001013", "가나"));
        expect(KcbManualTest.InvalidInput.class, () -> inputWith("SYN", "01000000000", "0002313", "가나"));
        expect(KcbManualTest.InvalidInput.class, () -> inputWith("SYN", "01000000000", "0001013000000", "가나"));
        expect(KcbManualTest.InvalidInput.class, () -> inputWith("SYN", "01000000000", "0001010", "가나"));
        expect(KcbManualTest.InvalidInput.class, () -> inputWith("SYN", "01000000000", "0001013", null));
    }

    @Test
    void 테스트5_잘못된프레임과코드와거래를거부한다() throws Exception {
        // given
        byte[] valid = reply(input(), "STEP1", TID, "0000", true);
        int[] corruptOffsets = {0, 56, 85, 158, 159, 263, 275, 287, 451, 456, 481, 493, 555, 571, 735, 2000};
        // when / then
        for (int offset : corruptOffsets) {
            byte[] changed = valid.clone();
            changed[offset] = '!';
            expect(KcbManualTest.InvalidResponse.class,
                    () -> KcbManualTest.response(changed, input(), "STEP1", ""));
        }
        expect(KcbManualTest.InvalidResponse.class,
                () -> KcbManualTest.response(Arrays.copyOf(valid, 2047), input(), "STEP1", ""));
        byte[] noIdentity = reply(input(), "STEP2", TID, "0000", true);
        Arrays.fill(noIdentity, 735, 895, (byte) ' ');
        eq("0000", KcbManualTest.response(noIdentity, input(), "STEP2", TID).code);
    }

    @Test
    void 테스트6_분할응답을끝까지읽는다() throws Exception {
        // given
        byte[] source = reply(input(), "STEP1", TID, "0000", true);
        byte[] target = new byte[2048];
        FakeSocket socket = new FakeSocket(new ByteArrayInputStream(source) {
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                return super.read(buffer, offset, Math.min(length, 3));
            }
        });
        // when
        KcbManualTest.readFully(socket, target, 0, target.length, deadline());
        // then
        ok(Arrays.equals(source, target));
        ok(socket.timeoutUpdates > 1);
    }

    @Test
    void 테스트7_짧거나지연된응답을거부한다() throws Exception {
        // given
        FakeSocket shortSocket = new FakeSocket(new ByteArrayInputStream(new byte[2]));
        FakeSocket emptySocket = new FakeSocket(new InputStream() {
            @Override public int read() { return 0; }
            @Override public int read(byte[] b, int off, int len) { return 0; }
        });
        // when / then
        expect(IOException.class, () -> KcbManualTest.readFully(shortSocket, new byte[8], 0, 8, deadline()));
        expect(IOException.class, () -> KcbManualTest.readFully(emptySocket, new byte[8], 0, 8, deadline()));
        expect(SocketTimeoutException.class,
                () -> KcbManualTest.readFully(shortSocket, new byte[8], 0, 8, System.nanoTime() - 1));
    }

    @Test
    void 테스트8_확인전에는전송하지않는다() throws Exception {
        // given
        ExchangeStub exchange = new ExchangeStub();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // when
        int result = run(exchange, output, "NO", "000000");
        // then
        eq(0, result);
        eq(0, exchange.calls);
        ok(text(output).contains("outcome=NOT_SENT"));
    }

    @Test
    void 테스트9_두단계단건만전송하고입력을출력하지않는다() throws Exception {
        // given
        ExchangeStub exchange = new ExchangeStub();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // when
        int result = run(exchange, output, "SEND", "000000");
        // then
        eq(0, result);
        eq(2, exchange.calls);
        ok(text(output).contains("step=STEP1 code=0000 outcome=ACCEPTED"));
        ok(text(output).contains("step=STEP2 code=0000 outcome=SUCCESS"));
        for (String secret : new String[] {input().phone, input().birthAndSex, input().name, TID,
                "000000", "SYN-CI", "SYN-DI"}) ok(!text(output).contains(secret));
        ok(allZero(exchange.lastResponse));
        ok(allZero(exchange.lastRequest));
    }

    @Test
    void 테스트10_첫단계실패후에는중단한다() throws Exception {
        // given
        ExchangeStub exchange = new ExchangeStub();
        exchange.code = "B001";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // when
        int result = run(exchange, output, "SEND", "000000");
        // then
        eq(1, result);
        eq(1, exchange.calls);
        ok(text(output).contains("outcome=REJECTED"));
    }

    @Test
    void 테스트11_통신실패는미확정으로표시하고재시도하지않는다() throws Exception {
        // given
        ExchangeStub exchange = new ExchangeStub();
        exchange.fail = true;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // when
        int result = run(exchange, output, "SEND", "000000");
        // then
        eq(3, result);
        eq(1, exchange.calls);
        ok(text(output).contains("outcome=UNKNOWN"));
        ok(!text(output).contains("PRIVATE_EXCEPTION_VALUE"));
    }

    @Test
    void 테스트12_인증번호취소와잘못된입력은두번째전송을막는다() throws Exception {
        // given
        String[] invalid = {"", "12345", "1234567", "12A456", null};
        // when / then
        for (String otp : invalid) {
            ExchangeStub exchange = new ExchangeStub();
            int result = run(exchange, new ByteArrayOutputStream(), "SEND", otp);
            ok(result != 0);
            eq(1, exchange.calls);
        }
    }

    @Test
    void 테스트13_두번째거래불일치는성공으로표시하지않는다() throws Exception {
        // given
        ExchangeStub exchange = new ExchangeStub();
        exchange.mismatchSecond = true;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // when
        int result = run(exchange, output, "SEND", "000000");
        // then
        eq(3, result);
        eq(2, exchange.calls);
        ok(text(output).contains("outcome=UNKNOWN"));
        ok(!text(output).contains("outcome=SUCCESS"));
    }

    @Test
    void 테스트14_오류응답의짧은본문을허용한다() {
        // given
        byte[] failure = reply(input(), "STEP2", TID, "XXX2", false);
        // when
        KcbManualTest.Result result = KcbManualTest.response(failure, input(), "STEP2", TID);
        // then
        eq(728, KcbManualTest.responseLength(failure));
        eq("XXX2", result.code);
    }

    @Test
    void 테스트15_개발설정을읽기전용으로검증한다() throws Exception {
        // given
        Path config = config("12345", "N", "N");
        // when
        KcbManualTest.validateConfiguration(options(), null, config, temporary.resolve("lguPlus"), "dev");
        KcbManualTest.validateConfiguration(options(), input(), config, temporary.resolve("lguPlus"), "dev");
        // then
        ok(Files.isRegularFile(config));
    }

    @Test
    void 테스트16_운영설정과가공응답설정과포트불일치를거부한다() throws Exception {
        // given
        Path config = config("12345", "N", "N");
        Path route = temporary.resolve("lguPlus");
        // when / then
        expect(KcbManualTest.ConfigurationFailure.class,
                () -> KcbManualTest.validateConfiguration(options(), input(), config, route, "prod"));
        expect(KcbManualTest.ConfigurationFailure.class,
                () -> KcbManualTest.validateConfiguration(options(), input(), config, route, null));
        Path wrongName = temporary.resolve("service-prod.xml");
        Files.copy(config, wrongName);
        expect(KcbManualTest.ConfigurationFailure.class,
                () -> KcbManualTest.validateConfiguration(options(), input(), wrongName, route, "dev"));
        config("12346", "N", "N");
        expect(KcbManualTest.ConfigurationFailure.class,
                () -> KcbManualTest.validateConfiguration(options(), input(), config, route, "dev"));
        config("12345", "Y", "N");
        expect(KcbManualTest.ConfigurationFailure.class,
                () -> KcbManualTest.validateConfiguration(options(), input(), config, route, "dev"));
        config("12345", "N", "Y");
        expect(KcbManualTest.ConfigurationFailure.class,
                () -> KcbManualTest.validateConfiguration(options(), input(), config, route, "dev"));
    }

    @Test
    void 테스트17_선택거래가다른기관으로전송되는것을막는다() throws Exception {
        // given
        Path config = config("12345", "N", "N");
        Path route = temporary.resolve("lguPlus");
        // when / then
        Files.write(route, "OTHER-CODE\n".getBytes(CertCharset.EUC_KR));
        KcbManualTest.validateConfiguration(options(), input(), config, route, "dev");
        Files.write(route, "OTHER-CODE\nSYN-KCB\n".getBytes(CertCharset.EUC_KR));
        expect(KcbManualTest.ConfigurationFailure.class,
                () -> KcbManualTest.validateConfiguration(options(), input(), config, route, "dev"));
        KcbManualTest.validateConfiguration(options(), null, config, route, "dev");
    }

    @Test
    void 테스트18_잘못된설정과엔티티선언을거부하고원문을출력하지않는다() throws Exception {
        // given
        Path config = temporary.resolve("service-dev.xml");
        String[] invalidXml = {"<service>PRIVATE_XML_DATA", "<!DOCTYPE service [<!ENTITY private 'PRIVATE_XML_DATA'>]><service/>",
                "<service><entity name='XPAY_LEASED' class='unexpected.Type'/></service>",
                "<service/>"};
        PrintStream originalError = System.err;
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();
        // when
        try {
            System.setErr(new PrintStream(capturedError, true, "UTF-8"));
            for (String xml : invalidXml) {
                Files.write(config, xml.getBytes(StandardCharsets.UTF_8));
                expect(KcbManualTest.ConfigurationFailure.class,
                        () -> KcbManualTest.validateConfiguration(options(), input(), config,
                                temporary.resolve("lguPlus"), "dev"));
            }
        } finally {
            System.setErr(originalError);
        }
        // then
        eq("", text(capturedError));
    }

    @Test
    void 테스트19_설정검증에실패하면송신확인전에중단한다() throws Exception {
        // given
        ExchangeStub exchange = new ExchangeStub();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // when
        int result = run(exchange, output, "SEND", "000000", (options, input) -> {
            throw new KcbManualTest.ConfigurationFailure("TEST_GUARD_REJECTED");
        });
        // then
        eq(2, result);
        eq(0, exchange.calls);
        ok(text(output).contains("outcome=NOT_SENT"));
        ok(!text(output).contains("event=manual.ready"));
    }

    @Test
    @Timeout(10)
    void 테스트20_실제루프백연결로분할응답한건을수신한다() throws Exception {
        // given
        byte[] expectedRequest = KcbManualTest.request(input(), "STEP1", "", "");
        byte[] syntheticResponse = reply(input(), "STEP1", TID, "0000", true);
        AtomicInteger connections = new AtomicInteger();
        AtomicReference<Throwable> serverFailure = new AtomicReference<Throwable>();
        try (ServerSocket listener = new ServerSocket()) {
            listener.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 1);
            listener.setSoTimeout(2000);
            Thread responder = new Thread(() -> {
                try {
                    try (Socket peer = listener.accept()) {
                        connections.incrementAndGet();
                        peer.setSoTimeout(2000);
                        byte[] received = new byte[633];
                        new DataInputStream(peer.getInputStream()).readFully(received);
                        ok(Arrays.equals(expectedRequest, received));
                        // 길이 접두부와 본문을 여러 번 나누어 보낸다.
                        peer.getOutputStream().write(syntheticResponse, 0, 3);
                        peer.getOutputStream().flush();
                        for (int position = 3; position < syntheticResponse.length; position += 17) {
                            int length = Math.min(17, syntheticResponse.length - position);
                            peer.getOutputStream().write(syntheticResponse, position, length);
                            peer.getOutputStream().flush();
                        }
                    }
                    // 클라이언트의 추가 접속이나 재전송이 없는지도 확인한다.
                    listener.setSoTimeout(200);
                    try (Socket unexpected = listener.accept()) {
                        connections.incrementAndGet();
                    } catch (SocketTimeoutException expected) {
                        // 추가 접속이 없는 것이 정상이다.
                    }
                } catch (Throwable failure) {
                    serverFailure.set(failure);
                }
            }, "manual-kcb-loopback-test");
            responder.setDaemon(true);
            responder.start();
            try {
                // when
                byte[] actual = new KcbManualTest.LocalExchange().exchange(expectedRequest, listener.getLocalPort(), 3000);
                KcbManualTest.Result result = KcbManualTest.response(actual, input(), "STEP1", "");
                responder.join(3000);
                // then
                ok(!responder.isAlive());
                if (serverFailure.get() != null) throw new AssertionError("Loopback responder failed", serverFailure.get());
                eq(1, connections.get());
                eq("0000", result.code);
                eq(TID, result.tid);
                ok(Arrays.equals(syntheticResponse, actual));
            } finally {
                listener.close();
                responder.join(1000);
                if (responder.isAlive()) responder.interrupt();
                ok(!responder.isAlive());
            }
        }
    }

    private Path config(String port, String step1, String step2) throws IOException {
        Path config = temporary.resolve("service-dev.xml");
        String xml = "<?xml version='1.0' encoding='UTF-8'?><service>"
                + "<entity name='XPAY_LEASED' class='scbk.cert.conf.ConfigBean'><serverPort>" + port
                + "</serverPort></entity><entity name='MOBILE_CERT' class='scbk.cert.conf.KCBConfigBean'>"
                + "<step1Mode>" + step1 + "</step1Mode><step2Mode>" + step2 + "</step2Mode></entity></service>";
        Files.write(config, xml.getBytes(StandardCharsets.UTF_8));
        return config;
    }

    private static int run(ExchangeStub exchange, ByteArrayOutputStream output, String confirm, String otp)
            throws Exception {
        return run(exchange, output, confirm, otp, (options, input) -> { });
    }

    private static int run(ExchangeStub exchange, ByteArrayOutputStream output, String confirm, String otp,
                           KcbManualTest.Guard guard) throws Exception {
        String[] answers = {"SYN-KCB", "SYN-DEPT", "SYN-OFFICE", "SYN", "1", "01000000000", "0001013",
                "가나", confirm, otp};
        KcbManualTest.Prompt prompt = new KcbManualTest.Prompt() {
            int position;
            @Override public String read(String label) { return answers[position++]; }
        };
        return KcbManualTest.run(options(), prompt, exchange, new PrintStream(output, true, "UTF-8"), guard);
    }

    private static KcbManualTest.Options options() {
        return KcbManualTest.Options.parse(new String[] {"--send", "--port", "12345"});
    }

    private static KcbManualTest.Input input() {
        return inputWith("SYN-DEPT", "01000000000", "0001013", "가나");
    }

    private static KcbManualTest.Input inputWith(String department, String phone, String birth, String name) {
        return new KcbManualTest.Input("SYN-KCB", department, "SYN-OFFICE", "SYN", "1", phone, birth, name);
    }

    private static byte[] reply(KcbManualTest.Input input, String step, String tid, String code, boolean full) {
        MCIHeaderBean header = new MCIHeaderBean();
        header.setTranCd(input.transactionCode);
        header.setChnlTypCd(input.channel);
        XPayAuthBean body = new XPayAuthBean();
        body.setMobileStep(step);
        body.setTid(tid);
        body.setMobileComm(input.carrier);
        body.setMobileNum(input.phone);
        body.setMobileSsn(input.birthAndSex);
        body.setBuyer(input.name);
        body.setBohoSvnYn("P");
        body.setIssuerCode(code);
        body.setCi("SYN-CI");
        body.setDi("SYN-DI");
        String data = CommonUtil.byteFiller(input.department, 12) + CommonUtil.byteFiller(input.office, 12)
                + code + CommonUtil.setFiller(160);
        return new MCIParser().getResponse(header, new MCITransBean(), "", data,
                body.getTransactionOld(full ? step : "STEP1"), code, "");
    }

    private static final class ExchangeStub implements KcbManualTest.Exchange {
        int calls;
        String code = "0000";
        boolean fail, mismatchSecond;
        byte[] lastRequest, lastResponse;
        @Override public byte[] exchange(byte[] request, int port, int timeout) throws IOException {
            calls++;
            lastRequest = request;
            if (fail) throw new IOException("PRIVATE_EXCEPTION_VALUE");
            eq(12345, port);
            eq(30000, timeout);
            eq(calls == 1 ? "STEP1" : "STEP2", ascii(request, 348, 5));
            if (calls == 2) {
                eq(TID, ascii(request, 353, 24));
                eq("000000", ascii(request, 413, 6));
            }
            lastResponse = reply(input(), calls == 1 ? "STEP1" : "STEP2",
                    mismatchSecond && calls == 2 ? "CERT20260101000000000002" : TID, code, true);
            return lastResponse;
        }
    }

    private static final class FakeSocket extends Socket {
        final InputStream input;
        int timeoutUpdates;
        FakeSocket(InputStream input) { this.input = input; }
        @Override public InputStream getInputStream() { return input; }
        @Override public synchronized void setSoTimeout(int timeout) {
            ok(timeout > 0);
            timeoutUpdates++;
        }
    }

    private static String ascii(byte[] value, int start, int length) {
        return new String(value, start, length, StandardCharsets.US_ASCII);
    }
    private static String text(ByteArrayOutputStream output) { return new String(output.toByteArray(), StandardCharsets.UTF_8); }
    private static long deadline() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(2); }
    private static boolean allZero(byte[] value) { for (byte b : value) if (b != 0) return false; return true; }
    private static void ok(boolean condition) { if (!condition) throw new AssertionError("Assertion failed"); }
    private static void eq(Object expected, Object actual) { ok(expected.equals(actual)); }
    interface Checked { void run() throws Exception; }
    private static void expect(Class<? extends Throwable> type, Checked action) throws Exception {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            throw new AssertionError("Unexpected failure type");
        }
        throw new AssertionError("Expected failure was absent");
    }
}
