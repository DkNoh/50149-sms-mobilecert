/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	SocketServer.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   Socket Wrapping 되어 있는 클라이언트용 class
 */

package scbk.cert.server;

import java.net.Socket;

public interface SocketServer {
	public void serve(Socket socket);
}
