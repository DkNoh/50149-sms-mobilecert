/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	SocketManager.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   Socket Wrapping 되어 있는 클라이언트용 class
 */

package scbk.cert.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SocketManager {

	public Socket socket = null;
	public InputStream inputStream = null;
	public OutputStream outputStream = null;

	private String host = "";
	private int port = 0;
	private int socketTimeout = 30000; // 30초
	private int soTimeout = 30000; // 30초

	public SocketManager() {
	}

	public SocketManager(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void setSocketTimeout(int socketTimeout, int soTimeout) {
		this.socketTimeout = socketTimeout;
		this.soTimeout = soTimeout;
	}

	public boolean isAlive() {
		if (socket.isOutputShutdown()) {
			return false;
		}
		if (socket.isClosed()) {
			return false;
		}
		return true;

	}

	public void setSocketTimeout(String socketTimeout, String soTimeout) {
		setSocketTimeout(Integer.parseInt(socketTimeout), Integer.parseInt(soTimeout));
	}

	public OutputStream getOutputStream() throws IOException {
		if (socket == null) {
			System.out.println("getOutputStream() Error");
			throw new IOException("socket is null");
		}
		return socket.getOutputStream();
	}

	public InputStream getInputStream() throws IOException {
		if (socket == null) {
			System.out.println("getInputStream() Error");
			throw new IOException("socket is null");
		}
		return socket.getInputStream();
	}

	public void connect() throws IOException {
		try {
			socket = new Socket();
			socket.connect(new InetSocketAddress(host, port), socketTimeout);

			socket.setSoTimeout(soTimeout);
			inputStream = getInputStream();
			outputStream = getOutputStream();
		} catch (IOException io) {
			System.out.println("connect() error");
			throw new IOException(io.getMessage());
		}
	}

	public void setSocket(Socket socket) throws IOException {
		this.socket = socket;
		inputStream = socket.getInputStream();
		outputStream = socket.getOutputStream();
	}

	public byte[] connect(byte[] request, int readLength) throws IOException {
		byte[] response = null;
		try {
			connect();
			outputStream = getOutputStream();
			send(request);
			inputStream = getInputStream();
			response = recv(readLength);
		} catch (IOException io) {
			throw new IOException(io.getMessage());
		} finally {
			ioClose();
			socketClose();
		}
		return response;
	}

	public void send(byte[] response) throws IOException {
		outputStream.write(response, 0, response.length);
		outputStream.flush();
	}

	public byte[] recv(int requestLength) throws IOException {
		byte[] recv = new byte[requestLength];

		boolean run = true;
		int receivedLength = 0;

		while (run) {
			byte[] temp = new byte[requestLength - receivedLength];
			int read = inputStream.read(temp);
			if (read <= 0) {
				run = false;
			} else {
				arrayCopy(recv, receivedLength, temp, read);
			}

			receivedLength += read;
			if (receivedLength >= requestLength) {
				run = false;
			}
		}
		return recv;
	}

	public byte[] recvAll() throws IOException {

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		int bcount = 0;
		byte[] buf = new byte[2048];
		int read_retry_count = 0;
		while (true) {
			int n = inputStream.read(buf);
			if (n > 0) {
				bcount += n;
				bout.write(buf, 0, n);
			} else if (n == -1)
				break;
			else { // n == 0
				if (++read_retry_count >= 5) {
					throw new IOException("inputstream-read-retry-count(5) exceed !");
				}
			}
			if (inputStream.available() == 0) {
				break;
			}
		}
		bout.flush();
		byte[] res = bout.toByteArray();
		bout.close();
		return res;

	}

	public void ioClose() throws IOException {
		if (inputStream != null) {
			inputStream.close();
		}
		if (outputStream != null) {
			outputStream.close();
		}
	}

	public void arrayCopy(Object destBuffer, int destPos, Object srcBuffer, int srcLen) {
		System.arraycopy(srcBuffer, 0, destBuffer, destPos, srcLen);
	}

	public void socketClose() {
		if (socket != null) {
			try {
				socket.close();
				socket = null;
			} catch (IOException io) {
			}
		}
	}
}
