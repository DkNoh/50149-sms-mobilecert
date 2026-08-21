/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	ServiceListener.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   서버 수신 처리 모듈 
 */
package scbk.cert.server;

import java.net.Socket;

import scbk.cert.conf.ConfigBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.main.Gateway;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;

public class ServiceListener {
	private ConfigBean configBean = null;
	private KCBConfigBean kcbConfigBean = null;
	private byte[] request = null;
	private byte[] response = null;
	private SocketManager socket = null;
	boolean receiveSuccess = false;
	private String specType = "";
	private String id = "";

	public ServiceListener() {

	}

	public ServiceListener(Socket receiveSocket, String id, ConfigBean configBean, KCBConfigBean kcbConfigBean) throws Exception {
		socket = new SocketManager();
		socket.setSocket(receiveSocket);
		this.configBean = configBean;
		this.kcbConfigBean = kcbConfigBean;
		this.id = "[" + id + "] ";
	}

	public void process() {
		try {
			byte[] specLength = socket.recv(8);

			if (CommonUtil.parseInt(specLength) <= 0) {
				throw new Exception("no listen");
			}

			request = socket.recv(CommonUtil.parseInt(specLength));

			if (request != null) {
				String reqMaskData = CommonUtil.setMaskingHost(CommonUtil.toString(request), "out");
				Log.write("log.day", id + " HOST>>MOBILE_CERT [" + reqMaskData + "]" + "length: " + reqMaskData.length(), this);
				Gateway gateway = new Gateway(configBean, kcbConfigBean, id);
				response = gateway.execute(request);
				socket.send(response);
				
			}
		} catch (Exception e) {
			if (request != null) {
				Log.write("log.day", id + " HOST<<MOBILE_CERT [RESONOSE ERROR]" + CommonUtil.getExceptionMessage(e), this);
			} else {
				Log.write("log.day", id + " MCI ACK", this);
			}
		} finally {
			if (request != null) {
				String resMaskData =  CommonUtil.setMaskingHost(CommonUtil.toString(response), "in");
				Log.write("log.day", id + " HOST<<MOBILE_CERT [" + resMaskData + "] length : " + resMaskData.length(), this);
			}
			socket.socketClose();
		}
	}

}
