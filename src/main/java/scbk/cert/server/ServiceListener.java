/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	ServiceListener.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   서버 수신 처리 모듈 
 */
package scbk.cert.server;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

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
		this.id = id;
	}

	public void process() {
		final long startedAt = System.nanoTime();
		try {
			byte[] specLength = socket.recv(8);

			if (CommonUtil.parseInt(specLength) <= 0) {
				throw new Exception("no listen");
			}

			request = socket.recv(CommonUtil.parseInt(specLength));

			if (request != null) {
				Log.debug("log.day", "event=mci.requestReceived bytes=" + request.length, this);
				Gateway gateway = new Gateway(configBean, kcbConfigBean, id);
				response = gateway.execute(request);
				socket.send(response);
				Log.debug("log.day", "event=mci.responseWritten bytes=" + response.length, this);
				Log.info("log.day", "event=mci.responseSent durationMs="
						+ TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), this);
				
			}
		} catch (Exception e) {
			if (request != null) {
				Log.error("log.day", "event=mci.responseFailed", e, this);
			} else if (e instanceof SocketTimeoutException) {
				Log.warn("log.day", "event=mci.inputTimeout", e, this);
			} else if (e instanceof IOException) {
				Log.warn("log.day", "event=mci.inputReadFailed", e, this);
			} else if (e instanceof RuntimeException) {
				Log.error("log.day", "event=mci.inputFailed", e, this);
			} else {
				Log.debug("log.day", "event=mci.inputEndedOrIncomplete", this);
			}
		} finally {
			socket.socketClose();
		}
	}

}
