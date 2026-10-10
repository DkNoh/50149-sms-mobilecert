/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	ServiceLauncher.java
 * Date			:	2021-03-09 
 * Version		:	1.0
 * Author		:	InfoTree  02-2224-3420
 * Comment      :   서버 수신 처리 모듈 
 */

package scbk.cert.server;

import scbk.cert.conf.ConfigBean;
import scbk.cert.dao.DBFactory;
import scbk.cert.util.CertCharset;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;
import scbk.cert.util.XMLFactory;

public class ServiceLauncher {

	private SocketService service;
	private int defaultPort = 30000;

	public ServiceLauncher() throws Exception {

	}

	public boolean start() throws Exception {
		ConfigBean configBean = (ConfigBean) XMLFactory.getEntity("XPAY_LEASED");

		if (configBean.getServerPort().equals("")) {
			service = new SocketService(defaultPort, new Server());
		} else {
			service = new SocketService(CommonUtil.parseInt(configBean.getServerPort()), new Server());
		}
		Log.debug("log.day", "event=service.listenerStarted", this);
		return true;
	}

	public void close() throws Exception {
		try {
			if (service != null) {
				service.close();
			}
		} finally {
			DBFactory.shutdown();
		}
	}

	public static void main(String[] args) {
		CertCharset.configureStandardStreams();
		try {
			scbk.cert.server.ServiceLauncher server = new scbk.cert.server.ServiceLauncher();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					server.close();
					Log.info("log.root", "event=service.stopped", server);
				} catch (Exception e) {
					Log.error("log.root", "event=service.shutdownFailed", e, server);
				}
			}, "mobilecert-shutdown"));
			if (server.start()) {
				Log.info("log.root", "event=service.started", server);
			} else {
				Log.error("log.root", "event=service.startFailed", server);
			}
		} catch (Exception e) {
			Log.error("log.root", "event=service.startFailed", e, null);
		}
	}
	
}
