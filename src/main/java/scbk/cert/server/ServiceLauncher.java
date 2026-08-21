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
		Log.write("log.day", "LGD_XPAY / KCB SERVER RUN = TRUE", this);
		return true;
	}

	public void close() throws Exception {
		service.close();
	}

	public static void main(String[] args) {
		try {
			scbk.cert.server.ServiceLauncher server = new scbk.cert.server.ServiceLauncher();
			if (server.start()) {
				System.out.println("Service Started ... ^^ LGD_XPAY / KCB SERVER.. (Mobile Cert..) ");
			} else {
				System.out.println("Service Start fail ");
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
	
}
