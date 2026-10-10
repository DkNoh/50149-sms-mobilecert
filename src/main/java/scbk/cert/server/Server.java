/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	Server.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730
 * Comment      :   서버 수신 처리 모듈 
 */

package scbk.cert.server;

import java.io.IOException;
import java.net.Socket;

import scbk.cert.conf.ConfigBean;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;
import scbk.cert.util.XMLFactory;

public class Server implements SocketServer {

	private ConfigBean configBean = null;
	private KCBConfigBean kcbConfigBean = null;
	private static String id = "";

	public void serve(Socket socket) {
		final String requestId = Log.beginRequest();
		try {
			configBean = (ConfigBean) XMLFactory.getEntity("XPAY_LEASED");
			kcbConfigBean = (KCBConfigBean) XMLFactory.getEntity("MOBILE_CERT");
			
			socket.setSoTimeout(CommonUtil.parseInt(configBean.getServerTimeout()));
			ServiceListener listener = new ServiceListener(socket, requestId, configBean, kcbConfigBean);

			listener.process();

		} catch (Exception e) {
			Log.error("log.root", "event=server.requestFailed", e, this);
		} finally {
			try {
				socket.close();
			} catch (IOException io) {
				Log.error("log.root", "event=server.socketCloseFailed", io, this);
			} finally {
				Log.endRequest();
			}
		}
	}

	public void setId() {
		int temp = CommonUtil.parseInt(id);
		temp++;
		if (temp > 9999) {
			temp = 1;
		}
		Server.id = CommonUtil.zerofill(temp, 4);
	}

}
