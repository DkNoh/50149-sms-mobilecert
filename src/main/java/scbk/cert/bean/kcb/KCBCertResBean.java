/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	KCBCertResBean.java
 * Date			:	2021-03-09
 * Version		:	1.0
 * Author		:	InfoTree  02-2224-3420 
 * Comment      :   휴대폰 본인인증 개별부 전문  
 */

package scbk.cert.bean.kcb;

import scbk.cert.util.CommonUtil;

public class KCBCertResBean {

	// MCI에서 전달받은 Body parsing 내용.
	private String oid = ""; // 주문번호
	private String mobileStep = ""; // STEP1 : 휴대폰 가입확인 , STEP2 : 휴대폰 본인 인증 요청
	private String tid = ""; // LG유플러스에서 생성한 거래 번호
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

	// new parameter
	private String sex = ""; // 주민번호7번째자리번호
	private String sitecode = ""; // DI정보를 위한 사이트 코드


	// KCB 전달 내용 추가.
	private String tranMode = ""; // 전문코드 (4)
	private String memberCode = ""; // 회원사코드 (12)
	private String tranNo = ""; // 거래일련번호 (30)
	private String responseCode = ""; // 응답코드 (4)
	private String responseMsg = ""; // 응답메시지 (100)
	private String certMode = ""; // 인증방식구분 (1)

	private String cryptKeyNo = ""; // 암호화 키 일련번호 (4)
	private String cryptCode = ""; // 암호화 알고리즘 코드 (4)
	private String di = ""; // DI값 [암호화] (160)
	private String ci = ""; // CI값 [암호화] (192)
	private String ci2 = ""; // CI2?값 [암호화] (192)
	private String ciVersion = ""; // CI 버전 (2)
	private String mobileNo = ""; // 휴대폰번호 [암호화] (32)
	private String name = ""; // 이름 [암호화] (96)
	private String birth = ""; // 생년월일 [암호화] (32)
	private String sexCode = ""; // 성별구분 (1)
	private String localCode = ""; // 내외국인 구분 (1)
	private String personalInfoCheckFlag = ""; // 개인정보매칭여부 (1)

	public KCBCertResBean() {
	}

	public KCBCertResBean(byte[] transaction) {
		tranMode = CommonUtil.toString(transaction, 130, 4).trim();
		memberCode = CommonUtil.toString(transaction, 134, 12).trim();
		tranNo = CommonUtil.toString(transaction, 146, 30).trim();
		responseCode = CommonUtil.toString(transaction, 176, 4).trim();
		responseMsg = CommonUtil.toString(transaction, 180, 100).trim();
		if ("CNSS".equals(tranMode)) {
			personalInfoCheckFlag = CommonUtil.toString(transaction, 280, 1).trim();
			ci = CommonUtil.toString(transaction, 281, 90).trim();
			di = CommonUtil.toString(transaction, 371, 70).trim();
		} else {
			certMode = CommonUtil.toString(transaction, 280, 1).trim();
		}

	}

	public KCBCertResBean(byte[] transaction, String resStep) {
		tranMode = CommonUtil.toString(transaction, 130, 4).trim();
		memberCode = CommonUtil.toString(transaction, 134, 12).trim();
		tranNo = CommonUtil.toString(transaction, 146, 30).trim();
		responseCode = CommonUtil.toString(transaction, 176, 4).trim();
		responseMsg = CommonUtil.toString(transaction, 180, 100).trim();
		if ("CNSS".equals(tranMode)) {
			cryptKeyNo = CommonUtil.toString(transaction, 280, 4).trim();
			cryptCode = CommonUtil.toString(transaction, 284, 4).trim();
			di = CommonUtil.toString(transaction, 288, 160).trim();
			ci = CommonUtil.toString(transaction, 448, 192).trim();
			ci2 = CommonUtil.toString(transaction, 640, 192).trim();
			ciVersion = CommonUtil.toString(transaction, 832, 2).trim();
			mobileNo = CommonUtil.toString(transaction, 834, 32).trim();
			name = CommonUtil.toString(transaction, 866, 96).trim();
			birth = CommonUtil.toString(transaction, 962, 32).trim();
			sexCode = CommonUtil.toString(transaction, 994, 1).trim();
			localCode = CommonUtil.toString(transaction, 995, 1).trim();
			personalInfoCheckFlag = CommonUtil.toString(transaction, 996, 1).trim();
		} else {
			certMode = CommonUtil.toString(transaction, 280, 1).trim();
		}
	}

	public String getTransaction() {
		StringBuffer sb = new StringBuffer();
		sb.append(CommonUtil.byteFiller(tranMode, 4));
		sb.append(CommonUtil.byteFiller(memberCode, 12));
		sb.append(CommonUtil.byteFiller(tranNo, 30));
		sb.append(CommonUtil.byteFiller(responseCode, 4));
		sb.append(CommonUtil.byteFiller(responseMsg, 100));
		if ("CNSS".equals(tranMode)) {
			sb.append(CommonUtil.byteFiller(personalInfoCheckFlag, 1));
			// lgu+와 길이차이 있음.. 확인해야함.
			sb.append(CommonUtil.byteFiller(ci, 90));
			// lgu+ 와 kcb 의 회원사 코드 상이하여 다르게 출력됨. 확인필요(KCB에 LGU+ 회원사코드 사용하도록 요청해야함)
			sb.append(CommonUtil.byteFiller(di, 70));
		} else {
			sb.append(CommonUtil.byteFiller(certMode, 1));
		}
//		Log.write("log.day", " sb.toString() [" + sb.toString() + "]", this);
		return sb.toString();
	}

	public String getMCIResponse1() {
		StringBuffer sb = new StringBuffer();
		
		sb.append(CommonUtil.byteFiller(oid, 64));
		sb.append(CommonUtil.byteFiller(mobileStep, 5));
		sb.append(CommonUtil.byteFiller(tid, 24));
		sb.append(CommonUtil.byteFiller(mobileComm, 1));
		sb.append(CommonUtil.byteFiller(mobileNum, 12));

		sb.append(CommonUtil.byteFiller(authMode, 10));
		sb.append(CommonUtil.byteFiller(authNumber, 6));
		sb.append(CommonUtil.byteFiller(callBack, 12));
		sb.append(CommonUtil.byteFiller(mertName, 20));
		sb.append(CommonUtil.byteFiller(nameCheckYn, 1));
		sb.append(CommonUtil.byteFiller(buyer, 15));
//		sb.append(CommonUtil.byteFiller(bohoSvnYn, 1));
		sb.append(CommonUtil.byteFiller(certMode, 1));
		sb.append(CommonUtil.byteFiller(responseCode, 4));
		sb.append(CommonUtil.byteFiller(responseMsg, 160));
		sb.append(CommonUtil.byteFiller(birth, 6));
		sb.append(CommonUtil.byteFiller(sex, 1));
		sb.append(CommonUtil.byteFiller(name, 10));
		sb.append(CommonUtil.byteFiller(sitecode, 12));
		
//		Log.write("log.day", " sb.toString() [" + sb.toString() + "]", this);
		return sb.toString();
	}
	
	public String getMCIResponse2() {
		StringBuffer sb = new StringBuffer();
		sb.append(CommonUtil.byteFiller(oid, 64));
		sb.append(CommonUtil.byteFiller(mobileStep, 5));
		sb.append(CommonUtil.byteFiller(tid, 24));
		sb.append(CommonUtil.byteFiller(mobileComm, 1));
		sb.append(CommonUtil.byteFiller(mobileNum, 12));

		sb.append(CommonUtil.byteFiller(authMode, 10));
		sb.append(CommonUtil.byteFiller(authNumber, 6));
		sb.append(CommonUtil.byteFiller(callBack, 12));
		sb.append(CommonUtil.byteFiller(mertName, 20));
		sb.append(CommonUtil.byteFiller(nameCheckYn, 1));
		sb.append(CommonUtil.byteFiller(buyer, 15));
		sb.append(CommonUtil.byteFiller(bohoSvnYn, 1));
		sb.append(CommonUtil.byteFiller(issuerCode, 4));
		sb.append(CommonUtil.byteFiller(issuerMsg, 160));
		sb.append(CommonUtil.byteFiller(birth, 6));
		sb.append(CommonUtil.byteFiller(sex, 1));
		sb.append(CommonUtil.byteFiller(name, 10));
		// sb.append(CommonUtil.byteFiller(sitecode,12 ));
		sb.append(CommonUtil.byteFiller(ci, 90));
		sb.append(CommonUtil.byteFiller(di, 70));
//		Log.write("log.day", " sb.toString() [" + sb.toString() + "]", this);
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

	public String getResponseCode() {
		return responseCode;
	}

	public void setResponseCode(String responseCode) {
		this.responseCode = responseCode;
	}

	public String getResponseMsg() {
		return responseMsg;
	}

	public void setResponseMsg(String responseMsg) {
		this.responseMsg = responseMsg;
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

	public String getCertMode() {
		return certMode;
	}

	public void setCertMode(String certMode) {
		this.certMode = certMode;
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

	public String getDi() {
		return di;
	}

	public void setDi(String di) {
		this.di = di;
	}

	public String getCi() {
		return ci;
	}

	public void setCi(String ci) {
		this.ci = ci;
	}

	public String getCi2() {
		return ci2;
	}

	public void setCi2(String ci2) {
		this.ci2 = ci2;
	}

	public String getCiVersion() {
		return ciVersion;
	}

	public void setCiVersion(String ciVersion) {
		this.ciVersion = ciVersion;
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

	public String getPersonalInfoCheckFlag() {
		return personalInfoCheckFlag;
	}

	public void setPersonalInfoCheckFlag(String personalInfoCheckFlag) {
		this.personalInfoCheckFlag = personalInfoCheckFlag;
	}

}
