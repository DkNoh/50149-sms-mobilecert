package scbk.cert.test;

import java.util.Scanner;

import scbk.cert.conf.ConfigBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.main.Gateway;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.XMLFactory;

public class TestKcbCert {
	
	private ConfigBean configBean = null;
	private KCBConfigBean kcbConfigBean = null;
	
	public TestKcbCert() {
		configBean = (ConfigBean) XMLFactory.getEntity("XPAY_LEASED");
		kcbConfigBean = (KCBConfigBean) XMLFactory.getEntity("MOBILE_CERT");
	}
	
	public void execute() {
		System.out.println("TEST STEP? (lg/kcb/sms)");
		String step = getSystemIn();
		int id = 1;
		Gateway gateway = new Gateway(configBean, kcbConfigBean, "" + id++);
		byte[] request = null;
		byte[] response = null;
		if(step.equals("lg")) {
			request = "02021042316550701NF01    05810.61.17.91     ADQSHUB_SMS_002                  HUB00        10019       0019                                            Y00000GL9-KI3-HS9                                                                                                                                                                                 STEP1                        101011111111 5212122999331                1588-1599   SC제일은행          N업무테스트                                                                                                                                                                          ".getBytes();
		} else if(step.equals("kcb")) {
//			request = "020210511152457010860004008410.204.232.235  MSQSYP0085010001                              1086098601400860         T                                  Y00000OG5-QY5-MQ5 9860140                                                                                                                                                                         STEP1                        201099710810 7909302109217PASS            15881599    SC제일은행          N신희경                                                                                                                                                                              ".getBytes();
//			request = "020210511152457010860004008410.204.232.235  MSQSYP0085010001                              1086098601400860         T                                  Y00000OG5-QY5-MQ5 9860140                                                                                                                                                                         STEP1                        201071675907 9001151100717SMS             15881599    SC제일은행          N최경빈                                                                                                                                                                              ".getBytes();
			request = "020210511152457010860004008410.204.232.235  MSQSYP0085010001                              1086098601400860         T                                  Y00000OG5-QY5-MQ5 9860140                                                                                                                                                                         STEP1                        101011111111 5212122999331SMS             15881599    SC제일은행          N테스트                                                                                                                                                                              ".getBytes();
		} else if(step.equals("sms")){

			System.out.println("T_ID?");
			String tid = getSystemIn();
			
			System.out.println("CERT ? (NUMBER)");
			String certNum = getSystemIn();
			if(certNum.equals("P")) { // PASS
				certNum = "      ";
			}
			System.out.println("certNum=" + certNum);
//			request = ("0202104120908090103790108031200.37.91.106   SRQSYP0085010002                 TER00M410032610379057355 0379                                            Y00000OG5-QY5-MQ5                                                                                                                                                                                 STEP2" + tid + "201099710810 7909302109217TTT       " + certNum + "1588-1599   SC제일은행          N신희경                                                                                                                                                                              ").getBytes();
//			request = ("0202104120908090103790108031200.37.91.106   SRQSYP0085010002                 TER00M410032610379057355 0379                                            Y00000OG5-QY5-MQ5                                                                                                                                                                                 STEP2" + tid + "201071675907 9001151100717TTT       " + certNum + "1588-1599   SC제일은행          N최경빈                                                                                                                                                                              ").getBytes();
			request = ("0202104120908090103790108031200.37.91.106   SRQSYP0085010002                 TER00M410032610379057355 0379                                            Y00000OG5-QY5-MQ5                                                                                                                                                                                 STEP2" + tid + "101011111111 5212122999331TTT       " + certNum + "1588-1599   SC제일은행          N테스트                                                                                                                                                                              ").getBytes();
			
		}
		
		response = gateway.execute(request);
		System.out.println("response[" + CommonUtil.toString(response) + "]");
		
	}
	public static void main(String[] args) throws Exception {
		scbk.cert.test.TestKcbCert t = new scbk.cert.test.TestKcbCert();
		t.execute();
	}
	
	public String getSystemIn() {
		Scanner scanner = new Scanner(System.in);
		String input = scanner.nextLine();
		return input.trim();
	}
}
