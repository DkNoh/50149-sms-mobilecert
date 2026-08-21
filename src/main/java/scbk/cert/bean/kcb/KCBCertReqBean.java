/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	KCBCertReqBean.java
 * Date			:	2021-03-09
 * Version		:	1.0
 * Author		:	InfoTree  02-2224-3420 
 * Comment      :   휴대폰 본인인증 개별부 요청 전문  
 */

package scbk.cert.bean.kcb;

import scbk.cert.util.CommonUtil;

public class KCBCertReqBean {

	// MCI에서 전달받은 Body parsing 내용.
	private String oid = ""; // 주문번호
	private String mobileStep = ""; // STEP1 : 휴대폰 가입확인 , STEP2 : 휴대폰 본인 인증 요청
	private String tid = ""; // 거래일련번호 LG유플러스에서 생성한 거래 번호
	private String mobileComm = ""; // 1:SKT,2:KT,3:LGT
	private String mobileNum = ""; // 인증할 휴대폰 번호
	private String mobileSsn = ""; // 주민번호
	private String authMode = ""; // 인증번호 SMS 발송 여부 ( 미발송시 NOSMS
	private String authNumber = ""; // 인증번호
	private String callBack = ""; // CALLBACK번호
	private String mertName = ""; // 인증번호 SMS 발송시 나타낼 가맹점명
	private String nameCheckYn = ""; // 실명확인유무 기본 N
	private String buyer = ""; // 가입자 실명 실명확인시만 사용
	private String bohoSvnYn = ""; // 휴대폰번호 보호서비스 이용유무(Y:가입,N:미가입)
	private String issuerCode = ""; // 이통사 응답코드
	private String issuerMsg = ""; // 이통사 응답 메세지
	private String customSmsmsg = ""; // 고객사정의 SMS문구
	private String authnumberlength = ""; // 인증번호길이

	private String sex = ""; // 주민번호7번째자리번호
	private String sitecode = ""; // DI정보를 위한 사이트 코드
	
	private String ci = "";
	private String di = "";
	
	// KCB 전달 내용 추가.
	private String tranMode = ""; // 전문코드 (4)
	private String memberCode = ""; // 회원사코드 (12)
	private String tranNo = ""; // 거래일련번호 (30)
	private String cryptKeyNo = ""; // 암호화 키 일련번호 (4)
	private String cryptCode = ""; // 암호화 알고리즘 코드 (4)
	private String mobileNo = ""; // 휴대폰번호 [암호화] (32)
	private String name = ""; // 이름 [암호화] (96)
	private String birth = ""; // 생년월일 [암호화] (32)
	private String sexCode = ""; // 성별구분 (1)
	private String localCode = ""; // 내외국인 구분 (1)
	private String mobileSvcCode = ""; // 통신사구분코드 (2)
	private String personalInfoFlag = ""; // 개인정보 수집 및 이용 동의 (1)
	private String uniqueIdInfoFlag = ""; // 고유식별정보 처리 동의 (1)
	private String securitySvcFlag = ""; // 본인확인서비스 이용약관 동의 (1)
	private String mobileSvcFlag = ""; // 통신사 이용약관 동의 (1)
	// FILLER (14) blank field
	private String channelCode = ""; // 채널코드 (16)
	private String smsMsgCode = ""; // 메시지구분코드 (4)
	private String reqUrl = ""; // 요청URL명 (50)
	private String reqIp = ""; // 요청서버 IP (16)
	private String userIp = ""; // 고객 IP (16)
	// FILLER (12) blank field

	private String certNo = ""; // SMS인증요청번호 (6)

	public KCBCertReqBean() {
	}

	public KCBCertReqBean(byte[] transaction) {
		mobileStep = CommonUtil.toString(transaction, 226, 5).trim();
		tid = CommonUtil.toString(transaction, 231, 24).trim();
		mobileComm = CommonUtil.toString(transaction, 255, 1).trim();
		mobileNum = CommonUtil.toString(transaction, 256, 12).trim();
		mobileSsn = CommonUtil.toString(transaction, 268, 13).trim();
		authMode = CommonUtil.toString(transaction, 281, 10).trim();
		authNumber = CommonUtil.toString(transaction, 291, 6).trim();
		callBack = CommonUtil.toString(transaction, 297, 12).trim();
		mertName = CommonUtil.toString(transaction, 309, 20).trim();
		nameCheckYn = CommonUtil.toString(transaction, 329, 1).trim();
		buyer = CommonUtil.toString(transaction, 330, 15).trim();
		bohoSvnYn = CommonUtil.toString(transaction, 345, 1).trim();
		issuerCode = CommonUtil.toString(transaction, 346, 4).trim();
		issuerMsg = CommonUtil.toString(transaction, 350, 160).trim();

		makeName();
	}

	public String makeName() {

		name = buyer;
		return name;
	}

	public KCBCertReqBean(String transaction) {
		this(transaction.getBytes());
	}

	public String getTransaction() {
		StringBuffer sb = new StringBuffer();
		sb.append(CommonUtil.byteFiller(mobileStep, 5));
		sb.append(CommonUtil.byteFiller(tid, 24));
		sb.append(CommonUtil.byteFiller(mobileComm, 1));
		sb.append(CommonUtil.byteFiller(mobileNum, 12));
		sb.append(CommonUtil.byteFiller(mobileSsn, 13));
		sb.append(CommonUtil.byteFiller(authMode, 10));
		sb.append(CommonUtil.byteFiller(authNumber, 6));
		sb.append(CommonUtil.byteFiller(callBack, 12));
		sb.append(CommonUtil.byteFiller(mertName, 20));
		sb.append(CommonUtil.byteFiller(nameCheckYn, 1));
		sb.append(CommonUtil.byteFiller(buyer, 15));
		sb.append(CommonUtil.byteFiller(bohoSvnYn, 1));
		sb.append(CommonUtil.byteFiller(issuerCode, 4));
		sb.append(CommonUtil.byteFiller(issuerMsg, 160));
		return sb.toString();
	}

	public String getMobileStep() {
		return mobileStep;
	}

	public void setMobileStep(String mobileStep) {
		this.mobileStep = mobileStep;
	}

	public String getTid() {
		return tid;
	}

	public void setTid(String tid) {
		this.tid = tid;
	}

	public String getMobileComm() {
		return mobileComm;
	}

	public void setMobileComm(String mobileComm) {
		this.mobileComm = mobileComm;
	}

	public String getMobileNum() {
		return mobileNum;
	}

	public void setMobileNum(String mobileNum) {
		this.mobileNum = mobileNum;
	}

	public String getMobileSsn() {
		return mobileSsn;
	}

	public void setMobileSsn(String mobileSsn) {
		this.mobileSsn = mobileSsn;
	}

	public String getAuthMode() {
		return authMode;
	}

	public void setAuthMode(String authMode) {
		this.authMode = authMode;
	}

	public String getAuthNumber() {
		return authNumber;
	}

	public void setAuthNumber(String authNumber) {
		this.authNumber = authNumber;
	}

	public String getCallBack() {
		return callBack;
	}

	public void setCallBack(String callBack) {
		this.callBack = callBack;
	}

	public String getMertName() {
		return mertName;
	}

	public void setMertName(String mertName) {
		this.mertName = mertName;
	}

	public String getNameCheckYn() {
		return nameCheckYn;
	}

	public void setNameCheckYn(String nameCheckYn) {
		this.nameCheckYn = nameCheckYn;
	}

	public String getBuyer() {
		return buyer;
	}

	public void setBuyer(String buyer) {
		this.buyer = buyer;
	}

	public String getBohoSvnYn() {
		return bohoSvnYn;
	}

	public void setBohoSvnYn(String bohoSvnYn) {
		this.bohoSvnYn = bohoSvnYn;
	}

	public String getIssuerCode() {
		return issuerCode;
	}

	public void setIssuerCode(String issuerCode) {
		this.issuerCode = issuerCode;
	}

	public String getIssuerMsg() {
		return issuerMsg;
	}

	public void setIssuerMsg(String issuerMsg) {
		this.issuerMsg = issuerMsg;
	}

	public String getOid() {
		return oid;
	}

	public void setOid(String oid) {
		this.oid = oid;
	}

	public String getCustomSmsmsg() {
		return customSmsmsg;
	}

	public void setCustomSmsmsg(String customSmsmsg) {
		this.customSmsmsg = customSmsmsg;
	}

	public String getAuthnumberlength() {
		return authnumberlength;
	}

	public void setAuthnumberlength(String authnumberlength) {
		this.authnumberlength = authnumberlength;
	}

	public String getSex() {
		return sex;
	}

	public void setSex(String sex) {
		this.sex = sex;
	}

	public String getSitecode() {
		return sitecode;
	}

	public void setSitecode(String sitecode) {
		this.sitecode = sitecode;
	}

	public String getCi() {
		return ci;
	}

	public void setCi(String ci) {
		this.ci = ci;
	}

	public String getDi() {
		return di;
	}

	public void setDi(String di) {
		this.di = di;
	}

	public String getTranMode() {
		return tranMode;
	}

	public void setTranMode(String tranMode) {
		this.tranMode = tranMode;
	}

	public String getMemberCode() {
		return memberCode;
	}

	public void setMemberCode(String memberCode) {
		this.memberCode = memberCode;
	}

	public String getTranNo() {
		return tranNo;
	}

	public void setTranNo(String tranNo) {
		this.tranNo = tranNo;
	}

	public String getCryptKeyNo() {
		return cryptKeyNo;
	}

	public void setCryptKeyNo(String cryptKeyNo) {
		this.cryptKeyNo = cryptKeyNo;
	}

	public String getCryptCode() {
		return cryptCode;
	}

	public void setCryptCode(String cryptCode) {
		this.cryptCode = cryptCode;
	}

	public String getMobileNo() {
		return mobileNo;
	}

	public void setMobileNo(String mobileNo) {
		this.mobileNo = mobileNo;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBirth() {
		return birth;
	}

	public void setBirth(String birth) {
		this.birth = birth;
	}

	public String getSexCode() {
		return sexCode;
	}

	public void setSexCode(String sexCode) {
		this.sexCode = sexCode;
	}

	public String getLocalCode() {
		return localCode;
	}

	public void setLocalCode(String localCode) {
		this.localCode = localCode;
	}

	public String getMobileSvcCode() {
		return mobileSvcCode;
	}

	public void setMobileSvcCode(String mobileSvcCode) {
		this.mobileSvcCode = mobileSvcCode;
	}

	public String getPersonalInfoFlag() {
		return personalInfoFlag;
	}

	public void setPersonalInfoFlag(String personalInfoFlag) {
		this.personalInfoFlag = personalInfoFlag;
	}

	public String getUniqueIdInfoFlag() {
		return uniqueIdInfoFlag;
	}

	public void setUniqueIdInfoFlag(String uniqueIdInfoFlag) {
		this.uniqueIdInfoFlag = uniqueIdInfoFlag;
	}

	public String getSecuritySvcFlag() {
		return securitySvcFlag;
	}

	public void setSecuritySvcFlag(String securitySvcFlag) {
		this.securitySvcFlag = securitySvcFlag;
	}

	public String getMobileSvcFlag() {
		return mobileSvcFlag;
	}

	public void setMobileSvcFlag(String mobileSvcFlag) {
		this.mobileSvcFlag = mobileSvcFlag;
	}

	public String getChannelCode() {
		return channelCode;
	}

	public void setChannelCode(String channelCode) {
		this.channelCode = channelCode;
	}

	public String getSmsMsgCode() {
		return smsMsgCode;
	}

	public void setSmsMsgCode(String smsMsgCode) {
		this.smsMsgCode = smsMsgCode;
	}

	public String getReqUrl() {
		return reqUrl;
	}

	public void setReqUrl(String reqUrl) {
		this.reqUrl = reqUrl;
	}

	public String getReqIp() {
		return reqIp;
	}

	public void setReqIp(String reqIp) {
		this.reqIp = reqIp;
	}

	public String getUserIp() {
		return userIp;
	}

	public void setUserIp(String userIp) {
		this.userIp = userIp;
	}

	public String getCertNo() {
		return certNo;
	}

	public void setCertNo(String certNo) {
		this.certNo = certNo;
	}

}
