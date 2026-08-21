package scbk.cert.test;

import java.util.Scanner;

import scbk.cert.bean.uplus.HeaderBean;
import scbk.cert.bean.uplus.XPayAuthBean;
import scbk.cert.conf.ConfigBean;
import scbk.cert.server.SocketManager;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.XMLFactory;
import java.nio.ByteBuffer;

public class TestSample {

	private ConfigBean configBean = null;
	private String tid = "";
	private static byte FIELD_DELIMITER = (byte) 0x1F;
	private static byte END_OF_SEGMENT = (byte) 0x1E;
	private static byte END_OF_JUNMUN = (byte) 0xFF;

	public TestSample() {
		configBean = (ConfigBean) XMLFactory.getEntity("XPAY_LEASED_TEST");
	}

	public void execute(String mode, String tid) {
		byte[] request = null;
		byte[] h = CommonUtil.byteFiller("00000687TEST", 98).getBytes();
		byte[] t = CommonUtil.byteFiller("10860testusr08602         ", 60).getBytes();
		byte[] conv = null;
		byte[] d1 = CommonUtil.byteFiller("IT1-WK5-ID3 123451234512    ", 188).getBytes();
		byte[] d2 = null;

		if (mode.equals("1")) {
			conv = "Y00000".getBytes();
			d2 = CommonUtil.byteFiller(
					"STEP1                         01096712522 1111112222222                            testmerchant        N               N    ",
							284).getBytes();
		} else if (mode.equals("2")) {
			conv = "Y00000".getBytes();
			String ch = "STEP2" + tid;
			d2 = CommonUtil
					.byteFiller(
							ch
									+ " 01094457837 1111112222222          1234              testmerchant        N               N    ",
							284).getBytes();
		} else {
			System.out.println("mode error(1:가입확인,2:인증)");
			System.exit(0);
		}

		ByteBuffer b = ByteBuffer.allocate(1024);
		b.put(h);
		b.put(t);
		b.put(END_OF_SEGMENT);
		b.put(conv);
		b.put(END_OF_SEGMENT);
		b.put(d1);
		b.put(d2);
		b.put(END_OF_SEGMENT);
		b.put(END_OF_JUNMUN);
		b.put(END_OF_JUNMUN);
		b.flip();
		b.rewind();
		request = b.array();

		byte[] response = null;
		try {
			SocketManager socket = new SocketManager("194.168.1.11", 9291);
			// SocketManager socket = new SocketManager("localhost",9291);
			int timeout = Integer.parseInt("30000");
			socket.setSocketTimeout(timeout, timeout);
			socket.connect();

			socket.send(request);
			System.out.println("TEST >> XPAY_NEW [" + new String(request) + "]");
			byte[] recvLen = socket.recv(8);
			System.out.println("recvLeng =[" + CommonUtil.toString(recvLen) + "]");
			response = socket.recv(CommonUtil.parseInt(recvLen));
			System.out.println("TEST << XPAY_NEW [" + new String(response) + "]");
		} catch (Exception e) {
			System.out.println("통신 장애 발생 =" + e.getMessage());
		} finally {
			System.out.println("종료");
		}
	}

	public String getSystemIn() {
		Scanner scanner = new Scanner(System.in);
		String input = scanner.nextLine();
		return input.trim();
	}

	public static void main(String[] args) {
		String modeType = args[0];
		String tid = args[1];
		scbk.cert.test.TestSample t = new scbk.cert.test.TestSample();
		t.execute(modeType, tid);
	}

}
