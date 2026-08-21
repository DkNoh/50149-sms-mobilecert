package scbk.cert.test;

import java.util.Scanner;

import scbk.cert.bean.uplus.HeaderBean;
import scbk.cert.bean.uplus.XPayAuthBean;
import scbk.cert.conf.ConfigBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.dao.KCBDAO;
import scbk.cert.server.SocketManager;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.XMLFactory;

public class TestClient {

	private ConfigBean configBean = null;
	private KCBConfigBean kcbConfigBean = null;
	private String tid = "";

	public TestClient() {
		configBean = (ConfigBean) XMLFactory.getEntity("XPAY_LEASED_TEST");
		kcbConfigBean = (KCBConfigBean) XMLFactory.getEntity("MOBILE_CERT");
	}

	public void execute() {
		System.out.println("1:SKT,2:KT,3:LGT ");
		String mobileComm = getSystemIn();
		System.out.println("phone number");
		String mobileNum = getSystemIn();
		System.out.println("ssn");
		String mobileSsn = getSystemIn();
		
		String table = kcbConfigBean.getCertTable();
		String table2 = kcbConfigBean.getCertInfoTable();
		KCBDAO dao = new KCBDAO(table, table2);
		String tranSerno = dao.getSerno();
		String currentDtm = CommonUtil.getCurrentDate("yyyyMMddHHmmss");
		String tranNo = "CERT" + currentDtm + CommonUtil.zerofill(tranSerno, 6);
		System.out.println("tranSerno[" + tranSerno + "] tranNo[" + tranNo + "]");
		
		HeaderBean headerBean = new HeaderBean();
		headerBean.setMid(configBean.getMid());
		headerBean.setTxName(configBean.getTxName());
		headerBean.setEncYn("N");
		headerBean.setEncType("");
		headerBean.setSendDate(CommonUtil.getCurrentDate("yyyyMMddHHmmss"));

		XPayAuthBean authBean = new XPayAuthBean();
		authBean.setMobileStep("STEP2");
		authBean.setMobileComm(mobileComm);
		authBean.setMobileNum(mobileNum);
		authBean.setMobileSsn(mobileSsn);
		authBean.setAuthMode("NOSMS"); // SMS 미발송시는 NOSMS
		authBean.setCallBack("0234463730");
		authBean.setMertName("SC");
		authBean.setNameCheckYn("N");
		authBean.setBuyer("SC제일은행");
		authBean.makeBirth();
		authBean.makeName();
		authBean.makeSex();
		authBean.setCi("cicicicicicicicicicicicicici");
		authBean.setDi("dididididididididididididdidi");

		String request = headerBean.getTransaction(authBean.getTransaction());
		byte[] response = null;
		try {
			SocketManager socket = new SocketManager(configBean.getLgdIp(), Integer.parseInt(configBean.getLgdPort()));
			int timeout = Integer.parseInt(configBean.getLgdTimeout());
			socket.setSocketTimeout(timeout, timeout);
			socket.connect();

			System.out.println("LGD >> " + configBean.getLgdIp() + " " + configBean.getLgdPort());
			socket.send(request.getBytes());
			System.out.println("XPAY_NEW >> LGD [" + new String(request) + "]");
			/*
			 * byte[] recvLen = socket.recv(7);
			 * System.out.println("XPAY_NEW << RCV ["
			 * +CommonUtil.toString(recvLen)+"]"); response =
			 * socket.recv(CommonUtil
			 * .parseInt(CommonUtil.toString(recvLen,1,6)));
			 * System.out.println("XPAY_NEW << LGD ["+new String(response)+"]");
			 */
			response = socket.recvAll();
			System.out.println("XPAY_NEW << LGD [" + new String(response) + "]");

			headerBean = new HeaderBean(response);
			authBean = new XPayAuthBean(response);

		} catch (Exception e) {
			System.out.println(e);
			System.out.println("Error =" + e.getMessage());
		} finally {
			System.out.println("code=" + headerBean.getRespCode());
			System.out.println("msg=" + headerBean.getRespMsg());
			System.out.println("no=" + authBean.getTid());
			tid = authBean.getTid();
		}
		if (headerBean.getRespCode().equals("0000")) {
			executeAuth();
		}
	}

	public boolean executeAuth() {

		System.out.println("인증번호를 입력하여 주세요!");
		String authNumber = getSystemIn();

		HeaderBean headerBean = new HeaderBean();
		headerBean.setMid(configBean.getMid());
		headerBean.setTxName(configBean.getTxName());
		headerBean.setEncYn("N");
		headerBean.setEncType("");
		headerBean.setSendDate(CommonUtil.getCurrentDate("yyyyMMddHHmmss"));

		XPayAuthBean authBean = new XPayAuthBean();
		authBean.setMobileStep("STEP2");
		authBean.setTid(tid);
		authBean.setAuthNumber(authNumber); // 인증번호

		String request = headerBean.getTransaction(authBean.getTransaction());
		byte[] response = null;
		try {
			SocketManager socket = new SocketManager(configBean.getLgdIp(), Integer.parseInt(configBean.getLgdPort()));
			int timeout = Integer.parseInt(configBean.getLgdTimeout());
			socket.setSocketTimeout(timeout, timeout);
			socket.connect();

			socket.send(request.getBytes());
			System.out.println("XPAY_NEW >> LGD [" + new String(request) + "]");
			byte[] recvLen = socket.recv(7);
			response = socket.recv(CommonUtil.parseInt(CommonUtil.toString(recvLen, 1, 6)));
			System.out.println("XPAY_NEW << LGD [" + new String(response) + "]");
			headerBean = new HeaderBean(response);
			authBean = new XPayAuthBean(response);
		} catch (Exception e) {
			System.out.println("통신 장애 발생 =" + e.getMessage());
		} finally {
			System.out.println("결과코드=" + headerBean.getRespCode());
			System.out.println("결과메세지=" + headerBean.getRespMsg());
			System.out.println("거래번호=" + authBean.getTid());
			tid = authBean.getTid();
		}

		if (headerBean.getRespCode().equals("0000")) {
			return true;
		} else {
			return false;
		}
	}

	public String getSystemIn() {
		Scanner scanner = new Scanner(System.in);
		String input = scanner.nextLine();
		return input.trim();
	}

	public void getTest() {
		System.out.println(CommonUtil.getCurrentDate("yyyyMMddHHmmss") + "00");
		System.out.println(CommonUtil.toString(CommonUtil.zerofill(1236, 8).getBytes()));
	}

	public static void main(String[] args) {
		scbk.cert.test.TestClient t = new scbk.cert.test.TestClient();
		t.execute();
		// t.getTest();
	}

}
