/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	ConfigBean.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   휴대폰 본인인증 서버 구성에 대한 BEAN  
 */

package scbk.cert.conf;

public class ConfigBean {

	private String serverPort = ""; // 서버 포트
	private String serverTimeout = ""; // 서버 Timeout
	private String lgdIp = ""; // LGDACOM IP
	private String lgdPort = ""; // LGDACOM PORT
	private String lgdTimeout = ""; // LGDACOM TIMEOUT
	private String ackServer = ""; // MONITORING SERVER IP
	private String mid = ""; // 가맹점 아이디
	private String txName = ""; // 전문종류
	private String timeOut = ""; // 타임아웃시간(통신)
	private String retryCnt = ""; // 재시도 횟수
	private String certTable = ""; // 테이블명
	private String step1Mode = ""; // Y = 테스트 응답
	private String step2Mode = ""; // Y = 테스트 응답
	private String testRespCode = ""; // 응답 코드
	private String testRespMsg = ""; // 응답 메세지

	public ConfigBean() {
	}

	public String getServerPort() {
		return serverPort;
	}

	public void setServerPort(String serverPort) {
		this.serverPort = serverPort;
	}

	public String getServerTimeout() {
		return serverTimeout;
	}

	public void setServerTimeout(String serverTimeout) {
		this.serverTimeout = serverTimeout;
	}

	public String getLgdIp() {
		return lgdIp;
	}

	public void setLgdIp(String lgdIp) {
		this.lgdIp = lgdIp;
	}

	public String getLgdPort() {
		return lgdPort;
	}

	public void setLgdPort(String lgdPort) {
		this.lgdPort = lgdPort;
	}

	public String getLgdTimeout() {
		return lgdTimeout;
	}

	public void setLgdTimeout(String lgdTimeout) {
		this.lgdTimeout = lgdTimeout;
	}

	public String getAckServer() {
		return ackServer;
	}

	public void setAckServer(String ackServer) {
		this.ackServer = ackServer;
	}

	public String getMid() {
		return mid;
	}

	public void setMid(String mid) {
		this.mid = mid;
	}

	public String getTxName() {
		return txName;
	}

	public void setTxName(String txName) {
		this.txName = txName;
	}

	public String getTimeOut() {
		return timeOut;
	}

	public void setTimeOut(String timeOut) {
		this.timeOut = timeOut;
	}

	public String getRetryCnt() {
		return retryCnt;
	}

	public void setRetryCnt(String retryCnt) {
		this.retryCnt = retryCnt;
	}

	public String getCertTable() {
		return certTable;
	}

	public void setCertTable(String certTable) {
		this.certTable = certTable;
	}

	public String getStep1Mode() {
		return step1Mode;
	}

	public void setStep1Mode(String step1Mode) {
		this.step1Mode = step1Mode;
	}

	public String getStep2Mode() {
		return step2Mode;
	}

	public void setStep2Mode(String step2Mode) {
		this.step2Mode = step2Mode;
	}

	public String getTestRespCode() {
		return testRespCode;
	}

	public void setTestRespCode(String testRespCode) {
		this.testRespCode = testRespCode;
	}

	public String getTestRespMsg() {
		return testRespMsg;
	}

	public void setTestRespMsg(String testRespMsg) {
		this.testRespMsg = testRespMsg;
	}

}
