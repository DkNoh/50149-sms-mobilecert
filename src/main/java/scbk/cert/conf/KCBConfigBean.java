/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	KCBConfigBean.java
 * Date			:	2021-03-09
 * Version		:	1.0
 * Author		:	InfoTree  02-2224-3420
 * Comment      :   휴대폰 본인인증 서버 구성에 대한 BEAN  
 */

package scbk.cert.conf;

public class KCBConfigBean {

	private String kcbIp = ""; // KCB IP
	private String kcbPort = ""; // KCB PORT
	private String kcbTimeout = ""; // KCB TIMEOUT
	private String ackServer = ""; // MONITORING SERVER IP
	private String txCode = ""; // Transaction Code
	private String connMemberCode = ""; // 대외계 연결용 회원사 코드
	private String memberCode = ""; // 회원사 코드
	private String tranKindCode = ""; // 전문종별코드
	private String bizTypeCode = ""; // 업무구분코드
	private String tranFlag = ""; // 송수신 Flag
	private String timeOut = ""; // 타임아웃시간(통신)
	private String retryCnt = ""; // 재시도 횟수

	private String certTable = ""; // 테이블명
	private String certInfoTable =""; // 테이블명2
	private String step1Mode = ""; // Y = 테스트 응답
	private String step2Mode = ""; // Y = 테스트 응답
	private String testRespCode = ""; // 응답 코드
	private String testRespMsg = ""; // 응답 메세지
	private String reqIp; // 요청IP
	private String reqUrl; // 요청URL

	public KCBConfigBean() {
	}

	public String getKcbIp() {
		return kcbIp;
	}

	public void setKcbIp(String kcbIp) {
		this.kcbIp = kcbIp;
	}

	public String getKcbPort() {
		return kcbPort;
	}

	public void setKcbPort(String kcbPort) {
		this.kcbPort = kcbPort;
	}

	public String getKcbTimeout() {
		return kcbTimeout;
	}

	public void setKcbTimeout(String kcbTimeout) {
		this.kcbTimeout = kcbTimeout;
	}

	public String getAckServer() {
		return ackServer;
	}

	public void setAckServer(String ackServer) {
		this.ackServer = ackServer;
	}

	public String getTxCode() {
		return txCode;
	}

	public void setTxCode(String txCode) {
		this.txCode = txCode;
	}

	public String getConnMemberCode() {
		return connMemberCode;
	}

	public void setConnMemberCode(String connMemberCode) {
		this.connMemberCode = connMemberCode;
	}

	public String getMemberCode() {
		return memberCode;
	}

	public void setMemberCode(String memberCode) {
		this.memberCode = memberCode;
	}

	public String getTranKindCode() {
		return tranKindCode;
	}

	public void setTranKindCode(String tranKindCode) {
		this.tranKindCode = tranKindCode;
	}

	public String getBizTypeCode() {
		return bizTypeCode;
	}

	public void setBizTypeCode(String bizTypeCode) {
		this.bizTypeCode = bizTypeCode;
	}

	public String getTranFlag() {
		return tranFlag;
	}

	public void setTranFlag(String tranFlag) {
		this.tranFlag = tranFlag;
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
	
	public String getCertInfoTable() {
		return certInfoTable;
	}
	
	public void setCertInfoTable(String certInfoTable) {
		this.certInfoTable = certInfoTable;
	}
	

	public String getReqIp() {
		return reqIp;
	}

	public void setReqIp(String reqIp) {
		this.reqIp = reqIp;
	}

	public String getReqUrl() {
		return reqUrl;
	}

	public void setReqUrl(String reqUrl) {
		this.reqUrl = reqUrl;
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
