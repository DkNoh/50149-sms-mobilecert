package scbk.cert.main;

import scbk.cert.util.CertCharset;

import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.server.SocketManager;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;

public class KCBCommunication {

	private KCBConfigBean configBean = null;
	private KCBHeaderBean reqHeaderBean = null;
	private KCBHeaderBean resHeaderBean = null;
	private KCBCertResBean certResBean = null;

	public KCBCommunication(KCBConfigBean configBean, String id) {
		this.configBean = configBean;
	}

	public KCBHeaderBean getResHeaderBean() {
		return resHeaderBean;
	}

	public KCBCertResBean getCertResBean() {
		return certResBean;
	}

	public byte[] socketComm(String kcbRequest, String deptCode) {

		byte[] returnData = null;
		long startedNanos = System.nanoTime();

		try {

			byte[] kcbResponse = null;

			SocketManager socket = new SocketManager(configBean.getKcbIp(), Integer.parseInt(configBean.getKcbPort()));
			int timeout = Integer.parseInt(configBean.getKcbTimeout());
			socket.setSocketTimeout(timeout, timeout);
			socket.connect();

			byte[] requestBytes = kcbRequest.getBytes(CertCharset.EUC_KR);
			socket.send(requestBytes);
			Log.debug("log.day", "event=KCB_REQUEST_SENT bytes=" + requestBytes.length, this);
			byte[] recvLen = socket.recv(4);
			kcbResponse = socket.recv(CommonUtil.parseInt(CommonUtil.toString(recvLen)));
			Log.debug("log.day", "event=KCB_RESPONSE_RECEIVED bytes=" + kcbResponse.length, this);

			resHeaderBean = new KCBHeaderBean(kcbResponse);
			certResBean = new KCBCertResBean(kcbResponse, "TRAN");
			Log.debug("log.day", "event=KCB_RESPONSE_PARSED headerCode=" + Log.code(resHeaderBean.getTranResponseCode())
					+ " bodyCode=" + Log.code(certResBean.getResponseCode())
					+ " elapsedMs=" + (System.nanoTime() - startedNanos) / 1000000L, this);
		} catch (Exception e) {
			Log.error("log.day", "event=KCB_COMMUNICATION_FAILED outcome=UNKNOWN", e, this);
			resHeaderBean = new KCBHeaderBean(kcbRequest.getBytes(CertCharset.EUC_KR));
			resHeaderBean.setTranResponseCode("XXXX");
		}

		returnData = (resHeaderBean.getTransaction() + certResBean.getTransaction()).getBytes(CertCharset.EUC_KR);
		return returnData;

	}

}
