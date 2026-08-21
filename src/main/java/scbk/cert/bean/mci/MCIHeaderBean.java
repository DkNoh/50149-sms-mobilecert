package scbk.cert.bean.mci;

import scbk.cert.util.CommonUtil;

public class MCIHeaderBean {

	private String crypDvCd = "";
	private String tmsgWrtgDt = "";
	private String tmsgWrtgTm = "";
	private String trscGrcoCd = "";
	private String tmsgCreSysNm = "";
	private String issSrlNo = "";
	private String ipv6Ard = "";
	private String inptDlvCd = "";
	private String envrInfoDvCd = "";
	private String rqstRspsDvCd = "";
	private String trscSyncDvCd = "";
	private String tranCd = "";
	private String tmsgRspsDtm = "";
	private String procRsltDvCd = "";
	private String chnlTypCd = "";
	private int mciNdNo = 0;
	private String mciSessIdNo = "";

	public MCIHeaderBean() {

	}

	public MCIHeaderBean(String transaction) {
		this(transaction.getBytes());
	}

	public MCIHeaderBean(byte[] transaction) {
		this.crypDvCd = CommonUtil.toString(transaction, 0, 1).trim();
		this.tmsgWrtgDt = CommonUtil.toString(transaction, 1, 8).trim();
		this.tmsgWrtgTm = CommonUtil.toString(transaction, 9, 6).trim();
		this.trscGrcoCd = CommonUtil.toString(transaction, 15, 2).trim();
		this.tmsgCreSysNm = CommonUtil.toString(transaction, 17, 8).trim();
		this.issSrlNo = CommonUtil.toString(transaction, 25, 3).trim();
		this.ipv6Ard = CommonUtil.toString(transaction, 28, 16).trim();
		this.inptDlvCd = CommonUtil.toString(transaction, 44, 1).trim();
		this.envrInfoDvCd = CommonUtil.toString(transaction, 45, 1).trim();
		this.rqstRspsDvCd = CommonUtil.toString(transaction, 46, 1).trim();
		this.trscSyncDvCd = CommonUtil.toString(transaction, 47, 1).trim();
		this.tranCd = CommonUtil.toString(transaction, 48, 12).trim();
		this.tmsgRspsDtm = CommonUtil.toString(transaction, 60, 16).trim();
		this.procRsltDvCd = CommonUtil.toString(transaction, 76, 1).trim();
		this.chnlTypCd = CommonUtil.toString(transaction, 77, 3).trim();
		this.mciNdNo = CommonUtil.parseInt(CommonUtil.toString(transaction, 80, 2).trim());
		this.mciSessIdNo = CommonUtil.toString(transaction, 82, 8).trim();
	}

	public String getTransaction() {
		StringBuffer transaction = new StringBuffer();
		transaction.append(CommonUtil.byteFiller(crypDvCd, 1));
		transaction.append(CommonUtil.byteFiller(tmsgWrtgDt, 8));
		transaction.append(CommonUtil.byteFiller(tmsgWrtgTm, 6));
		transaction.append(CommonUtil.byteFiller(trscGrcoCd, 2));
		transaction.append(CommonUtil.byteFiller(tmsgCreSysNm, 8));
		transaction.append(CommonUtil.byteFiller(issSrlNo, 3));
		transaction.append(CommonUtil.byteFiller(ipv6Ard, 16));
		transaction.append(CommonUtil.byteFiller(inptDlvCd, 1));
		transaction.append(CommonUtil.byteFiller(envrInfoDvCd, 1));
		transaction.append(CommonUtil.byteFiller(rqstRspsDvCd, 1));
		transaction.append(CommonUtil.byteFiller(trscSyncDvCd, 1));
		transaction.append(CommonUtil.byteFiller(tranCd, 12));
		transaction.append(CommonUtil.byteFiller(tmsgRspsDtm, 16));
		transaction.append(CommonUtil.byteFiller(procRsltDvCd, 1));
		transaction.append(CommonUtil.byteFiller(chnlTypCd, 3));
		transaction.append(CommonUtil.zerofill(mciNdNo, 2));
		transaction.append(CommonUtil.byteFiller(mciSessIdNo, 8));

		return transaction.toString();
	}

	public String getCrypDvCd() {
		return crypDvCd;
	}

	public void setCrypDvCd(String crypDvCd) {
		this.crypDvCd = crypDvCd;
	}

	public String getTmsgWrtgDt() {
		return tmsgWrtgDt;
	}

	public void setTmsgWrtgDt(String tmsgWrtgDt) {
		this.tmsgWrtgDt = tmsgWrtgDt;
	}

	public String getTmsgWrtgTm() {
		return tmsgWrtgTm;
	}

	public void setTmsgWrtgTm(String tmsgWrtgTm) {
		this.tmsgWrtgTm = tmsgWrtgTm;
	}

	public String getTrscGrcoCd() {
		return trscGrcoCd;
	}

	public void setTrscGrcoCd(String trscGrcoCd) {
		this.trscGrcoCd = trscGrcoCd;
	}

	public String getTmsgCreSysNm() {
		return tmsgCreSysNm;
	}

	public void setTmsgCreSysNm(String tmsgCreSysNm) {
		this.tmsgCreSysNm = tmsgCreSysNm;
	}

	public String getIssSrlNo() {
		return issSrlNo;
	}

	public void setIssSrlNo(String issSrlNo) {
		this.issSrlNo = issSrlNo;
	}

	public String getIpv6Ard() {
		return ipv6Ard;
	}

	public void setIpv6Ard(String ipv6Ard) {
		this.ipv6Ard = ipv6Ard;
	}

	public String getInptDlvCd() {
		return inptDlvCd;
	}

	public void setInptDlvCd(String inptDlvCd) {
		this.inptDlvCd = inptDlvCd;
	}

	public String getEnvrInfoDvCd() {
		return envrInfoDvCd;
	}

	public void setEnvrInfoDvCd(String envrInfoDvCd) {
		this.envrInfoDvCd = envrInfoDvCd;
	}

	public String getRqstRspsDvCd() {
		return rqstRspsDvCd;
	}

	public void setRqstRspsDvCd(String rqstRspsDvCd) {
		this.rqstRspsDvCd = rqstRspsDvCd;
	}

	public String getTrscSyncDvCd() {
		return trscSyncDvCd;
	}

	public void setTrscSyncDvCd(String trscSyncDvCd) {
		this.trscSyncDvCd = trscSyncDvCd;
	}

	public String getTranCd() {
		return tranCd;
	}

	public void setTranCd(String tranCd) {
		this.tranCd = tranCd;
	}

	public String getTmsgRspsDtm() {
		return tmsgRspsDtm;
	}

	public void setTmsgRspsDtm(String tmsgRspsDtm) {
		this.tmsgRspsDtm = tmsgRspsDtm;
	}

	public String getProcRsltDvCd() {
		return procRsltDvCd;
	}

	public void setProcRsltDvCd(String procRsltDvCd) {
		this.procRsltDvCd = procRsltDvCd;
	}

	public String getChnlTypCd() {
		return chnlTypCd;
	}

	public void setChnlTypCd(String chnlTypCd) {
		this.chnlTypCd = chnlTypCd;
	}

	public int getMciNdNo() {
		return mciNdNo;
	}

	public void setMciNdNo(int mciNdNo) {
		this.mciNdNo = mciNdNo;
	}

	public String getMciSessIdNo() {
		return mciSessIdNo;
	}

	public void setMciSessIdNo(String mciSessIdNo) {
		this.mciSessIdNo = mciSessIdNo;
	}

}
