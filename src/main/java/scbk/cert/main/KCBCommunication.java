package scbk.cert.main;

import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.server.SocketManager;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;

public class KCBCommunication {

	private KCBConfigBean configBean = null;
	private String id = "";
	private KCBHeaderBean reqHeaderBean = null;
	private KCBHeaderBean resHeaderBean = null;
	private KCBCertResBean certResBean = null;

	public KCBCommunication(KCBConfigBean configBean, String id) {
		this.configBean = configBean;
		this.id = "[" + id + "]"; 
	}

	public KCBHeaderBean getResHeaderBean() {
		return resHeaderBean;
	}

	public KCBCertResBean getCertResBean() {
		return certResBean;
	}

	public byte[] socketComm(String kcbRequest, String deptCode) {

		byte[] returnData = null;

		try {

			byte[] kcbResponse = null;

			SocketManager socket = new SocketManager(configBean.getKcbIp(), Integer.parseInt(configBean.getKcbPort()));
			int timeout = Integer.parseInt(configBean.getKcbTimeout());
			socket.setSocketTimeout(timeout, timeout);
			socket.connect();

			socket.send(kcbRequest.getBytes());
			String reqMaskData = CommonUtil.setMaskingKcb(CommonUtil.toString(kcbRequest), "out");
			Log.write("log.day", id + " MOBILE_CERT >> KCB [" + reqMaskData + "]" + " length : " + CommonUtil.toString(kcbRequest).length(), this);
			byte[] recvLen = socket.recv(4);
			kcbResponse = socket.recv(CommonUtil.parseInt(CommonUtil.toString(recvLen)));
			String resMaskData = CommonUtil.setMaskingKcb(CommonUtil.toString(kcbResponse), "in");
			Log.write("log.day", id + " MOBILE_CERT << KCB [" + resMaskData + "]" + " length : " + CommonUtil.toString(kcbResponse).length(), this);

			resHeaderBean = new KCBHeaderBean(kcbResponse);
			certResBean = new KCBCertResBean(kcbResponse, "TRAN");
			Log.write("log.day", id + "response CI [" + certResBean.getCi() + "], DI [" + certResBean.getDi() + "]", this);
		} catch (Exception e) {
			Log.write("log.day", id + " EXCEPTION [" + CommonUtil.getExceptionMessage(e) + "]", this);
			resHeaderBean = new KCBHeaderBean(kcbRequest.getBytes());
			resHeaderBean.setTranResponseCode("XXXX");
		}

		returnData = (resHeaderBean.getTransaction() + certResBean.getTransaction()).getBytes();
		return returnData;

	}

}
