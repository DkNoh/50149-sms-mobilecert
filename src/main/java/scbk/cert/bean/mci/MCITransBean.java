package scbk.cert.bean.mci;

import scbk.cert.util.CommonUtil;

public class MCITransBean {

	private String tmsgMsgTypCd = "";
	private int blngBrNo = 0;
	private String empNo = "";
	private int txnBrNo = 0;
	private String serverId = "";
	private String termId = "";
	private String cancelKey = "";
	private String mciSvrDummy01 = "";

	public MCITransBean() {
	}

	public MCITransBean(String transaction) {
		this(transaction.getBytes());
	}

	public MCITransBean(byte[] transaction) {
		this.tmsgMsgTypCd = CommonUtil.toString(transaction, 0, 1).trim();
		this.blngBrNo = CommonUtil.parseInt(CommonUtil.toString(transaction, 1, 4).trim());
		this.empNo = CommonUtil.toString(transaction, 5, 7).trim();
		this.txnBrNo = CommonUtil.parseInt(CommonUtil.toString(transaction, 12, 4).trim());
		this.serverId = CommonUtil.toString(transaction, 16, 1).trim();
		this.termId = CommonUtil.toString(transaction, 17, 8).trim();
		this.cancelKey = CommonUtil.toString(transaction, 25, 1).trim();
		this.mciSvrDummy01 = CommonUtil.toString(transaction, 26, 34).trim();
	}

	public String getTransaction() {
		StringBuffer transaction = new StringBuffer();
		transaction.append(CommonUtil.byteFiller(tmsgMsgTypCd, 1));
		transaction.append(CommonUtil.zerofill(blngBrNo, 4));
		transaction.append(CommonUtil.byteFiller(empNo, 7));
		transaction.append(CommonUtil.zerofill(txnBrNo, 4));
		transaction.append(CommonUtil.byteFiller(serverId, 1));
		transaction.append(CommonUtil.byteFiller(termId, 8));
		transaction.append(CommonUtil.byteFiller(cancelKey, 1));
		transaction.append(CommonUtil.byteFiller(mciSvrDummy01, 34));

		return transaction.toString();
	}

	public String getTmsgMsgTypCd() {
		return tmsgMsgTypCd;
	}

	public void setTmsgMsgTypCd(String tmsgMsgTypCd) {
		this.tmsgMsgTypCd = tmsgMsgTypCd;
	}

	public int getBlngBrNo() {
		return blngBrNo;
	}

	public void setBlngBrNo(int blngBrNo) {
		this.blngBrNo = blngBrNo;
	}

	public String getEmpNo() {
		return empNo;
	}

	public void setEmpNo(String empNo) {
		this.empNo = empNo;
	}

	public int getTxnBrNo() {
		return txnBrNo;
	}

	public void setTxnBrNo(int txnBrNo) {
		this.txnBrNo = txnBrNo;
	}

	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	public String getTermId() {
		return termId;
	}

	public void setTermId(String termId) {
		this.termId = termId;
	}

	public String getCancelKey() {
		return cancelKey;
	}

	public void setCancelKey(String cancelKey) {
		this.cancelKey = cancelKey;
	}

	public String getMciSvrDummy01() {
		return mciSvrDummy01;
	}

	public void setMciSvrDummy01(String mciSvrDummy01) {
		this.mciSvrDummy01 = mciSvrDummy01;
	}

}
