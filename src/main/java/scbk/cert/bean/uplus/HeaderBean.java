/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	HeaderBean.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   휴대폰 본인인증 공통부 전문  
 */
package scbk.cert.bean.uplus;

import scbk.cert.util.CertCharset;

import scbk.cert.util.CommonUtil;

public class HeaderBean {

	private String mid = ""; // LG U+ 가입시 받은 가맹점 ID
	private String txName = ""; // Ping, MobileAuthOnly
	private String encYn = "N"; // 암호화 여부 N
	private String encType = ""; //
	private String sendDate = ""; // 요청일시 /응답일시 YYYYMMDDHHMISS
	private String respCode = ""; // 0000 : 성공, 외 실패
	private String respMsg = ""; // 결과메세지 TXT

	public HeaderBean() {
	}

	public HeaderBean(byte[] transaction) {
		mid = CommonUtil.toString(transaction, 0, 20).trim();
		txName = CommonUtil.toString(transaction, 20, 25).trim();
		encYn = CommonUtil.toString(transaction, 45, 1).trim();
		encType = CommonUtil.toString(transaction, 46, 2).trim();
		sendDate = CommonUtil.toString(transaction, 48, 14).trim();
		respCode = CommonUtil.toString(transaction, 62, 4).trim();
		respMsg = CommonUtil.toString(transaction, 66, 160).trim();
	}

	public String getTransaction(String part) {
		StringBuffer transaction = new StringBuffer();
		transaction.append(CommonUtil.byteFiller(mid, 20));
		transaction.append(CommonUtil.byteFiller(txName, 25));
		transaction.append(CommonUtil.byteFiller(encYn, 1));
		transaction.append(CommonUtil.byteFiller(encType, 2));
		transaction.append(CommonUtil.byteFiller(sendDate, 14));
		transaction.append(CommonUtil.byteFiller(respCode, 4));
		transaction.append(CommonUtil.byteFiller(respMsg, 160));

		String header = transaction.toString();
		String data = header + part;
		return "B" + CommonUtil.zerofill(data.getBytes(CertCharset.EUC_KR).length, 6) + data;
	}

	public String getTransaction() {
		StringBuffer transaction = new StringBuffer();
		transaction.append(CommonUtil.byteFiller(mid, 20));
		transaction.append(CommonUtil.byteFiller(txName, 25));
		transaction.append(CommonUtil.byteFiller(encYn, 1));
		transaction.append(CommonUtil.byteFiller(encType, 2));
		transaction.append(CommonUtil.byteFiller(sendDate, 14));
		transaction.append(CommonUtil.byteFiller(respCode, 4));
		transaction.append(CommonUtil.byteFiller(respMsg, 160));

		return transaction.toString();
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

	public String getEncYn() {
		return encYn;
	}

	public void setEncYn(String encYn) {
		this.encYn = encYn;
	}

	public String getEncType() {
		return encType;
	}

	public void setEncType(String encType) {
		this.encType = encType;
	}

	public String getSendDate() {
		return sendDate;
	}

	public void setSendDate(String sendDate) {
		this.sendDate = sendDate;
	}

	public String getRespCode() {
		return respCode;
	}

	public void setRespCode(String respCode) {
		this.respCode = respCode;
	}

	public String getRespMsg() {
		return respMsg;
	}

	public void setRespMsg(String respMsg) {
		this.respMsg = respMsg;
	}

}
