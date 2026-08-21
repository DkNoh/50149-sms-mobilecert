package scbk.cert.main;

import scbk.cert.bean.uplus.HeaderBean;
import scbk.cert.bean.uplus.XPayAuthBean;
import scbk.cert.conf.ConfigBean;
import scbk.cert.server.SocketManager;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;

public class Communication {

	private ConfigBean configBean = null;
	private String id = "";
	private HeaderBean reqHeaderBean = null;
	private HeaderBean resHeaderBean = null;
	private XPayAuthBean resXpayAuthBean = null;

	public Communication(ConfigBean configBean, String id) {
		this.configBean = configBean;
		this.id = id;
	}

	public HeaderBean getResHeaderBean() {
		return resHeaderBean;
	}

	public XPayAuthBean getResXpayAuthBean() {
		return resXpayAuthBean;
	}

	public byte[] socketComm(String reqData) {

		byte[] returnData = null;
		String step = "";
		try {
			// si add
			XPayAuthBean xPayAuthReqBean = new XPayAuthBean(CommonUtil.setFiller(226) + reqData);
			step = xPayAuthReqBean.getMobileStep();

			String lgdRequest = null;
			byte[] lgdResponse = null;

			reqHeaderBean = new HeaderBean();
			reqHeaderBean.setMid(configBean.getMid());
			// si add
			if (step != null && step.equals("STEP2")) {
				reqHeaderBean.setTxName(configBean.getTxName() + "_2");
			} else {
				reqHeaderBean.setTxName(configBean.getTxName());
			}
			reqHeaderBean.setEncYn("N");
			reqHeaderBean.setEncType("");
			reqHeaderBean.setSendDate(CommonUtil.getCurrentDate("yyyyMMddHHmmss"));
			// lgdRequest = reqHeaderBean.getTransaction(reqData);
			// System.out.println("#########"+step+"########");
			if (step.equals("STEP1")) {
				lgdRequest = reqHeaderBean.getTransaction(xPayAuthReqBean.getTransaction());
			} else {
				lgdRequest = reqHeaderBean.getTransaction(xPayAuthReqBean.getTransactionStep2());
			}

			SocketManager socket = new SocketManager(configBean.getLgdIp(), Integer.parseInt(configBean.getLgdPort()));
			int timeout = Integer.parseInt(configBean.getLgdTimeout());
			socket.setSocketTimeout(timeout, timeout);
			socket.connect();

			socket.send(lgdRequest.getBytes());
			Log.write("log.day", id + "XPAY >> LGD [" + new String(lgdRequest) + "]", this);
			// Log.write("log.day",id+"XPAY >> LGD ["+new
			// String(lgdRequest).substring(233,238)+"]", this);
			byte[] recvLen = socket.recv(7);
			lgdResponse = socket.recv(CommonUtil.parseInt(CommonUtil.toString(recvLen, 1, 6)));
			
			// 개발/운영 체크 개발일때 CI값 강제로 셋팅하여 보내기.
			String serverType = System.getProperty("scbk.cert.server.type", "dev");
			Log.write("log.day", id + " serverType [" + serverType + "]", this);
			//20180530 ci정보 셋팅 테스트전문
			if (step.equals("STEP2") && serverType.equals("dev")) {
				String testRes = "scbk1               MobileAuthOnlySub_2         201806150030570000본인확인성공                                                                                                                                                                                                                    STEP2201806150030450163355849101011112222                                                                                                                                                                                                                                      5001011테스트    LGCsg/Nyce11eNuIpztP1J2xzZjrAuvcF7VKPS42uUshKuMRAtGARycacT1DyUJJZ7ndm+vEmTuGXP1k4rBEgA==                                                                        ";
				lgdResponse = testRes.getBytes();
				System.out.println("**** step2222222222222222 **********" +CommonUtil.toString(lgdResponse));
			} 
			else {
				System.out.println("**** step1111111111111111 **********" +CommonUtil.toString(lgdResponse));
			}
			
			Log.write("log.day", id + "XPAY << LGD [" + CommonUtil.toString(lgdResponse) + "]", this);
			// Log.write("log.day",id+"XPAY << LGD TID ["+CommonUtil.toString(lgdResponse).substring(227,251)+"] RESULT_CD ["+CommonUtil.toString(lgdResponse).substring(62,66)+"]",
			// this);
			reqHeaderBean = new HeaderBean(lgdResponse);
			resXpayAuthBean = new XPayAuthBean(lgdResponse, step);
		} catch (Exception e) {
			Log.write("log.day", id + "EXCEPTION [" + CommonUtil.getExceptionMessage(e) + "]", this);
			reqHeaderBean.setRespCode("XXXX");
			reqHeaderBean.setRespMsg("통신장애 발생 " + e.getMessage());
			resXpayAuthBean = new XPayAuthBean(CommonUtil.setFiller(226) + reqData);
			resXpayAuthBean.setIssuerCode(reqHeaderBean.getRespCode());
			resXpayAuthBean.setIssuerMsg(reqHeaderBean.getRespMsg());
		}
		
		returnData = (reqHeaderBean.getTransaction() + resXpayAuthBean.getTransactionNew(step)).getBytes();
		
		return returnData;

	}

}
