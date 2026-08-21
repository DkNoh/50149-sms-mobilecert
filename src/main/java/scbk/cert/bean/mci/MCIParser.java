package scbk.cert.bean.mci;

import java.nio.ByteBuffer;

import scbk.cert.util.CommonUtil;

public class MCIParser {

	private static byte FIELD_DELIMITER = (byte) 0x1F;
	private static byte END_OF_SEGMENT = (byte) 0x1E;
	private static byte END_OF_JUNMUN = (byte) 0xFF;

	private byte[] systemHeader = new byte[90];
	private byte[] transactionHeader = new byte[60];
	private byte[] conv = null;
	private byte[] data = null;
	private int pos = 0;

	public MCIParser() {
	}

	public MCIParser(byte[] transaction) {
		parsing(transaction);
	}

	public byte[] getSystemHeader() {
		return systemHeader;
	}

	public byte[] getTransactionHeader() {
		return transactionHeader;
	}

	public byte[] getConv() {
		return conv;
	}

	public byte[] getData() {
		return data;
	}

	public void parsing(byte[] transaction) {
		// if(transaction != null && transaction.length > 0) {
		System.arraycopy(transaction, 0, systemHeader, 0, 90);
		System.arraycopy(transaction, 90, transactionHeader, 0, 60);

		byte[] temp = new byte[transaction.length - 151];
		System.arraycopy(transaction, 151, temp, 0, transaction.length - 151);
		conv = getBytess(temp, END_OF_SEGMENT);
		data = getBytess(temp, END_OF_SEGMENT);
		/*
		 * } else { conv = new byte[60]; data = new byte[60]; }
		 */
		/*
		 * Log.write("log.day", "======== MCI Parser Start ========",this);
		 * Log.write("log.day", "length = ["+transaction.length+"]",this);
		 * Log.write("log.day", "systemHeader=["+new
		 * String(systemHeader).trim()+"]",this); Log.write("log.day",
		 * "transactionHeader=["+new String(transactionHeader).trim()+"]",this);
		 * Log.write("log.day", "temp=["+new String(temp).trim()+"]",this);
		 * Log.write("log.day", "conv=["+new String(conv).trim()+"]",this);
		 * Log.write("log.day", "data=["+new String(data).trim()+"]",this);
		 * Log.write("log.day", "======== MCI Parser END   ========",this);
		 */
	}

	public byte[] getBytess(byte[] temp, byte delChar) {
		ByteBuffer buffer = ByteBuffer.allocate(9216);
		for (int i = pos; i < temp.length; i++) {
			if (temp[i] == delChar || temp[i] == 0x00) {
				break;
			} else {
				buffer.put(temp[i]);
				pos++;
			}
		}
		pos++;
		buffer.rewind();
		return buffer.array();
	}

	public byte[] getResponse(MCIHeaderBean mciHeaderBean, MCITransBean mciTransBean
							, String reconv, String hdata, String redata
							, String resultCd, String resultMsg) {
		byte[] response = null;

		// Log.write("log.day", "redata=["+redata+"]",this);

		// 2012.04.04 MCI 출력 데이터 포멧 변경
		int recvLeng = (mciHeaderBean.getTransaction()
						+ mciTransBean.getTransaction() + resultCd
						+ CommonUtil.byteFiller(resultMsg, 100) + hdata + redata).getBytes().length;
		recvLeng = recvLeng + 2; // delimiter length 적용
		ByteBuffer resp = ByteBuffer.allocate(2048);
		resp.put(CommonUtil.toString(CommonUtil.zerofill(recvLeng, 8)).getBytes());
		resp.put(mciHeaderBean.getTransaction().getBytes()); // 시스템 헤더부
		resp.put(mciTransBean.getTransaction().getBytes()); // 거래 공통부
		resp.put(END_OF_SEGMENT);
		resp.put(resultCd.getBytes());
		resp.put(CommonUtil.byteFiller(resultMsg, 100).getBytes());
		resp.put(hdata.getBytes()); // 출력 데이터부 헤더
		resp.put(redata.getBytes()); // 출력 데이터부 정보
		resp.put(FIELD_DELIMITER);
		resp.flip();
		resp.rewind();

		response = resp.array();

		return response;
	}

	public void MCIParserTest() {
		byte[] response = null;
		byte[] ddd = CommonUtil.byteFiller("TEST", 150).getBytes();
		ByteBuffer b = ByteBuffer.allocate(1024);
		b.put(ddd);
		b.put(END_OF_SEGMENT);
		b.put("CONV".getBytes());
		b.put(END_OF_SEGMENT);
		b.put("DATA".getBytes());
		b.put(END_OF_SEGMENT);
		b.put(END_OF_JUNMUN);
		b.put(END_OF_JUNMUN);
		b.flip();
		b.rewind();
		response = b.array();

		String data = CommonUtil.toString(response);
		int leng = CommonUtil.toString(response).length();

		data = CommonUtil.zerofill(leng, 8) + data;

//		System.out.println("data = [" + data + "]");
//		System.out.println("length = [" + CommonUtil.toString(response).length() + "]");

		parsing(data.substring(8).getBytes());
		// parsing(response);
	}

	public static void main(String[] args) {
		scbk.cert.bean.mci.MCIParser dd = new scbk.cert.bean.mci.MCIParser();
		dd.MCIParserTest();
	}

}
