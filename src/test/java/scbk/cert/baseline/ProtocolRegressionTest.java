package scbk.cert.baseline;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.util.CommonUtil;

class ProtocolRegressionTest {
    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    @Test
    void 테스트코드1_영문필드의_고정길이와공백을유지한다() {
        // given
        String[] values = {"", "ABCD", "ABCDE", "ABCDEF", " AB "};

        // when
        byte[][] actual = new byte[values.length][];
        for (int i = 0; i < values.length; i++) {
            actual[i] = bytes(CommonUtil.byteFiller(values[i], 5));
        }

        // then
        assertArrayEquals(bytes("     "), actual[0]);
        assertArrayEquals(bytes("ABCD "), actual[1]);
        assertArrayEquals(bytes("ABCDE"), actual[2]);
        assertArrayEquals(bytes("ABCDE"), actual[3]);
        assertArrayEquals(bytes(" AB  "), actual[4]);
    }

    @Test
    void 테스트코드2_한글필드의_바이트길이와경계절단을유지한다() {
        // given
        String value = "가나";

        // when
        byte[] exact = bytes(CommonUtil.byteFiller(value, 4));
        byte[] padded = bytes(CommonUtil.byteFiller("가", 3));
        byte[] truncated = bytes(CommonUtil.byteFiller(value, 2));
        byte[] split = bytes(CommonUtil.byteFiller(value, 3));

        // then
        assertArrayEquals(hex("b0a1b3aa"), exact);
        assertArrayEquals(hex("b0a120"), padded);
        assertArrayEquals(hex("b0a1"), truncated);
        // 기존 기준 바이트는 잘린 한글의 나머지를 물음표로 대체한다.
        assertArrayEquals(hex("b0a13f"), split);
    }

    @Test
    void 테스트코드3_잘못된숫자와범위초과를_기존규칙대로처리한다() {
        // given
        String malformed = "12X456";
        String overflow = "9999999999";
        byte[] valid = bytes("9999");

        // when
        int malformedResult = CommonUtil.parseInt(malformed);
        int overflowResult = CommonUtil.parseInt(overflow);
        int validResult = CommonUtil.parseInt(valid);

        // then
        assertEquals(0, malformedResult);
        assertEquals(0, overflowResult);
        assertEquals(9999, validResult);
    }

    @Test
    void 테스트코드4_잘못된필드입력의_기존예외를유지한다() {
        // given
        byte[] empty = new byte[0];
        byte[] shortField = bytes("ABC");

        // when
        Executable trimEmpty = () -> CommonUtil.byteTrim(empty, 3);
        Executable readPastEnd = () -> CommonUtil.toString(shortField, 2, 2);
        Executable fillNull = () -> CommonUtil.byteFiller(null, 3);

        // then
        assertThrows(ArrayIndexOutOfBoundsException.class, trimEmpty);
        assertThrows(StringIndexOutOfBoundsException.class, readPastEnd);
        assertThrows(NullPointerException.class, fillNull);
    }

    @Test
    void 테스트코드5_KCB요청을_기존기준바이트와동일하게생성한다() throws Exception {
        // given
        KCBCertReqBean request = syntheticRequest();
        byte[] expected = concat(
                fixed("SYN", 5), fixed("SYN-TX", 24), fixed("X", 1),
                spaces(12), spaces(13), spaces(10), spaces(6), spaces(12),
                fixed("가나", 20), spaces(1), fixed(" A ", 15), spaces(1),
                fixed("E", 4), fixed("테스트", 160));

        // when
        byte[] actual = bytes(request.getTransaction());

        // then
        assertEquals(284, actual.length);
        assertArrayEquals(expected, actual);
        assertEquals("e5cfbe2e0d57d26eff2ae6a596a449fab89aa09f292d4a7db7cbbcc8f9e5f1e5",
                sha256(actual));
    }

    @Test
    void 테스트코드6_KCB요청을_정해진바이트위치에서해석한다() {
        // given
        byte[] frame = spaces(510);
        put(frame, 226, "SYN");
        put(frame, 231, "SYN-TX");
        put(frame, 255, "X");
        put(frame, 309, "가나");
        put(frame, 330, " A ");
        put(frame, 346, "E");
        put(frame, 350, "테스트");

        // when
        KCBCertReqBean actual = new KCBCertReqBean(frame);

        // then
        assertEquals("SYN", actual.getMobileStep());
        assertEquals("SYN-TX", actual.getTid());
        assertEquals("X", actual.getMobileComm());
        assertEquals("가나", actual.getMertName());
        assertEquals("A", actual.getBuyer());
        assertEquals("A", actual.getName());
        assertEquals("E", actual.getIssuerCode());
        assertEquals("테스트", actual.getIssuerMsg());
    }

    @Test
    void 테스트코드7_KCB공통헤더를_고정길이로생성한다() {
        // given
        KCBHeaderBean header = new KCBHeaderBean();
        header.setTranCode("SYN-TX");
        header.setConnMemberCode("SYNMEM");
        header.setTranKindCode("0700");
        header.setBizTypeCode("400");
        header.setTranFlag("B");
        header.setTranResponseCode("0000");
        header.setKcbTranSerno("0000001");
        header.setKcbTranDtm("20260824123456");
        header.setTranSerno("0000002");
        header.setTranDtm("20260824123500");

        // when
        byte[] actual = bytes(header.getTransaction());

        // then
        assertEquals(130, actual.length);
        assertArrayEquals(bytes("SYN-TX   SYNMEM  0700400B0000000000120260824123456000000220260824123500"),
                Arrays.copyOfRange(actual, 0, 71));
        assertArrayEquals(spaces(59), Arrays.copyOfRange(actual, 71, 130));
    }

    @Test
    void 테스트코드8_KCB공통헤더를_정해진바이트위치에서해석한다() {
        // given
        byte[] frame = spaces(130);
        put(frame, 0, "SYN-TX");
        put(frame, 9, "SYNMEM");
        put(frame, 17, "0700");
        put(frame, 21, "400");
        put(frame, 24, "B");
        put(frame, 25, "0000");
        put(frame, 29, "0000001");
        put(frame, 36, "20260824123456");
        put(frame, 50, "0000002");
        put(frame, 57, "20260824123500");

        // when
        KCBHeaderBean actual = new KCBHeaderBean(frame);

        // then
        assertEquals("SYN-TX", actual.getTranCode());
        assertEquals("SYNMEM", actual.getConnMemberCode());
        assertEquals("0700", actual.getTranKindCode());
        assertEquals("400", actual.getBizTypeCode());
        assertEquals("B", actual.getTranFlag());
        assertEquals("0000", actual.getTranResponseCode());
        assertEquals("0000001", actual.getKcbTranSerno());
        assertEquals("20260824123456", actual.getKcbTranDtm());
        assertEquals("0000002", actual.getTranSerno());
        assertEquals("20260824123500", actual.getTranDtm());
    }

    @Test
    void 테스트코드9_KCB일반응답의_한글과필드위치를유지한다() {
        // given
        byte[] frame = responseFrame("BSX", 281);
        put(frame, 280, "S");

        // when
        KCBCertResBean actual = new KCBCertResBean(frame);
        byte[] serialized = bytes(actual.getTransaction());

        // then
        assertEquals("BSX", actual.getTranMode());
        assertEquals("SYN-MEMBER", actual.getMemberCode());
        assertEquals("SYN-TRANSACTION", actual.getTranNo());
        assertEquals("0000", actual.getResponseCode());
        assertEquals("인증성공", actual.getResponseMsg());
        assertEquals("S", actual.getCertMode());
        assertEquals(151, serialized.length);
        assertArrayEquals(Arrays.copyOfRange(frame, 130, 281), serialized);
    }

    @Test
    void 테스트코드10_KCB인증응답의_일반식별정보길이를유지한다() {
        // given
        byte[] frame = responseFrame("CNSS", 441);
        put(frame, 280, "Y");
        put(frame, 281, "SYN-CI");
        put(frame, 371, "SYN-DI");

        // when
        KCBCertResBean actual = new KCBCertResBean(frame);
        byte[] serialized = bytes(actual.getTransaction());

        // then
        assertEquals("Y", actual.getPersonalInfoCheckFlag());
        assertEquals("SYN-CI", actual.getCi());
        assertEquals("SYN-DI", actual.getDi());
        assertEquals(311, serialized.length);
        assertArrayEquals(Arrays.copyOfRange(frame, 130, 441), serialized);
    }

    @Test
    void 테스트코드11_KCB인증응답의_암호화필드를정해진위치에서해석한다() {
        // given
        byte[] frame = responseFrame("CNSS", 997);
        put(frame, 280, "0001");
        put(frame, 284, "0002");
        put(frame, 288, "SYN-DI");
        put(frame, 448, "SYN-CI");
        put(frame, 640, "SYN-CI2");
        put(frame, 832, "01");
        put(frame, 834, "SYN-MOBILE");
        put(frame, 866, "SYN-NAME");
        put(frame, 962, "SYN-BIRTH");
        put(frame, 994, "1");
        put(frame, 995, "0");
        put(frame, 996, "Y");

        // when
        KCBCertResBean actual = new KCBCertResBean(frame, "STEP2");

        // then
        assertEquals("0001", actual.getCryptKeyNo());
        assertEquals("0002", actual.getCryptCode());
        assertEquals("SYN-DI", actual.getDi());
        assertEquals("SYN-CI", actual.getCi());
        assertEquals("SYN-CI2", actual.getCi2());
        assertEquals("01", actual.getCiVersion());
        assertEquals("SYN-MOBILE", actual.getMobileNo());
        assertEquals("SYN-NAME", actual.getName());
        assertEquals("SYN-BIRTH", actual.getBirth());
        assertEquals("1", actual.getSexCode());
        assertEquals("0", actual.getLocalCode());
        assertEquals("Y", actual.getPersonalInfoCheckFlag());
    }

    @Test
    void 테스트코드12_KCB전문이한바이트부족하면_기존예외를유지한다() {
        // given
        byte[] shortRequest = spaces(509);
        byte[] shortHeaderFields = spaces(70);
        byte[] shortResponse = responseFrame("CNSS", 440);

        // when
        Executable parseRequest = () -> new KCBCertReqBean(shortRequest);
        Executable parseHeader = () -> new KCBHeaderBean(shortHeaderFields);
        Executable parseResponse = () -> new KCBCertResBean(shortResponse);

        // then
        assertThrows(StringIndexOutOfBoundsException.class, parseRequest);
        assertThrows(StringIndexOutOfBoundsException.class, parseHeader);
        assertThrows(StringIndexOutOfBoundsException.class, parseResponse);
    }

    @Test
    void 테스트코드13_KCB요청필드가길이를초과하면_후속필드위치를유지한다() {
        // given
        KCBCertReqBean request = new KCBCertReqBean();
        request.setMobileStep("STEP12");
        request.setMertName("가나다라마바사아자차카");
        request.setNameCheckYn("Y");

        // when
        byte[] actual = bytes(request.getTransaction());

        // then
        assertEquals(284, actual.length);
        assertArrayEquals(bytes("STEP1"), Arrays.copyOfRange(actual, 0, 5));
        assertArrayEquals(bytes("가나다라마바사아자차"), Arrays.copyOfRange(actual, 83, 103));
        assertEquals((byte) 'Y', actual[103]);
        assertArrayEquals(spaces(15), Arrays.copyOfRange(actual, 104, 119));
    }

    private static KCBCertReqBean syntheticRequest() {
        KCBCertReqBean request = new KCBCertReqBean();
        request.setMobileStep("SYN");
        request.setTid("SYN-TX");
        request.setMobileComm("X");
        request.setMertName("가나");
        request.setBuyer(" A ");
        request.setIssuerCode("E");
        request.setIssuerMsg("테스트");
        return request;
    }

    private static byte[] responseFrame(String mode, int length) {
        byte[] frame = spaces(length);
        put(frame, 130, mode);
        put(frame, 134, "SYN-MEMBER");
        put(frame, 146, "SYN-TRANSACTION");
        put(frame, 176, "0000");
        put(frame, 180, "인증성공");
        return frame;
    }

    private static void put(byte[] target, int offset, String value) {
        byte[] source = bytes(value);
        System.arraycopy(source, 0, target, offset, source.length);
    }

    private static byte[] fixed(String value, int width) {
        byte[] result = spaces(width);
        byte[] source = bytes(value);
        System.arraycopy(source, 0, result, 0, Math.min(source.length, width));
        return result;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(EUC_KR);
    }

    private static byte[] spaces(int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, (byte) ' ');
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
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte item : digest) {
            result.append(String.format("%02x", item & 0xff));
        }
        return result.toString();
    }
}
