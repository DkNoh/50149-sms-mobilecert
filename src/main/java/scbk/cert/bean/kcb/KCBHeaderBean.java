/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	KCBHeaderBean.java
 * Date			:	2021-03-09 
 * Version		:	1.0
 * Author		:	InfoTree  02-2224-3420 
 * Comment      :   휴대폰 본인인증 공통부 전문  
 */
package scbk.cert.bean.kcb;

import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;

public class KCBHeaderBean {

	private String tranCode = ""; // Transaction Code (9)
	private String connMemberCode = ""; // 대외계 회원사코드 (8)
	private String tranKindCode = ""; // 전문종별코드 (4)
	private String bizTypeCode = ""; // 업무구분코드 (3)
	private String tranFlag = ""; // 송수신 Flag (1)
	private String tranResponseCode = ""; // 응답코드 (4)
	private String kcbTranSerno = ""; // KCB 전문 관리번호 (7)
	private String kcbTranDtm = ""; // KCB 전문 전송시간 (14)
	private String tranSerno = ""; // 회원사 전문 관리번호 (7)
	private String tranDtm = ""; // 회원사 전문전송 시간 (14)

	private String tranResponseMsg = ""; // 응답메시지 (곧통정보부)

	public KCBHeaderBean() {
	}

	public KCBHeaderBean(byte[] transaction) {
		tranCode = CommonUtil.toString(transaction, 0, 9).trim();
		connMemberCode = CommonUtil.toString(transaction, 9, 8).trim();
		tranKindCode = CommonUtil.toString(transaction, 17, 4).trim();
		bizTypeCode = CommonUtil.toString(transaction, 21, 3).trim();
		tranFlag = CommonUtil.toString(transaction, 24, 1).trim();
		tranResponseCode = CommonUtil.toString(transaction, 25, 4).trim();
		kcbTranSerno = CommonUtil.toString(transaction, 29, 7).trim();
		kcbTranDtm = CommonUtil.toString(transaction, 36, 14).trim();
		tranSerno = CommonUtil.toString(transaction, 50, 7).trim();
		tranDtm = CommonUtil.toString(transaction, 57, 14).trim();
	}

	public String getTransaction() {

		StringBuffer transaction = new StringBuffer();

		transaction.append(CommonUtil.byteFiller(tranCode, 9));
		transaction.append(CommonUtil.byteFiller(connMemberCode, 8));
		transaction.append(CommonUtil.byteFiller(tranKindCode, 4));
		transaction.append(CommonUtil.byteFiller(bizTypeCode, 3));
		transaction.append(CommonUtil.byteFiller(tranFlag, 1));
		transaction.append(CommonUtil.byteFiller(tranResponseCode, 4));
		transaction.append(CommonUtil.byteFiller(kcbTranSerno, 7));
		transaction.append(CommonUtil.byteFiller(kcbTranDtm, 14));
		transaction.append(CommonUtil.byteFiller(tranSerno, 7));
		transaction.append(CommonUtil.byteFiller(tranDtm, 14));
		transaction.append(CommonUtil.byteFiller("", 16));
		transaction.append(CommonUtil.byteFiller("", 43));
//		Log.write("log.day", " transaction.toString() [" + transaction.toString() + "]", this);

		return transaction.toString();
	}

	public String getTranCode() {
		return tranCode;
	}

	public void setTranCode(String tranCode) {
		this.tranCode = tranCode;
	}

	public String getConnMemberCode() {
		return connMemberCode;
	}

	public void setConnMemberCode(String connMemberCode) {
		this.connMemberCode = connMemberCode;
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

	public String getTranResponseCode() {
		return tranResponseCode;
	}

	public void setTranResponseCode(String tranResponseCode) {
		this.tranResponseCode = tranResponseCode;
	}

	public String getKcbTranSerno() {
		return kcbTranSerno;
	}

	public void setKcbTranSerno(String kcbTranSerno) {
		this.kcbTranSerno = kcbTranSerno;
	}

	public String getKcbTranDtm() {
		return kcbTranDtm;
	}

	public void setKcbTranDtm(String kcbTranDtm) {
		this.kcbTranDtm = kcbTranDtm;
	}

	public String getTranSerno() {
		return tranSerno;
	}

	public void setTranSerno(String tranSerno) {
		this.tranSerno = tranSerno;
	}

	public String getTranDtm() {
		return tranDtm;
	}

	public void setTranDtm(String tranDtm) {
		this.tranDtm = tranDtm;
	}

	public String getTranResponseMsg() {
		return tranResponseMsg;
	}

	public void setTranResponseMsg(String tranResponseMsg) {
		this.tranResponseMsg = tranResponseMsg;
	}

}
