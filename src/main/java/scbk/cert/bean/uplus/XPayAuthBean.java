/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	XPayAuthBean.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   휴대폰 본인인증 개별부 전문  
 */

package scbk.cert.bean.uplus;

import scbk.cert.util.CertCharset;

import scbk.cert.util.CommonUtil;

public class XPayAuthBean {

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
	private String birth = ""; // 생년월일(YYMMDD)
	private String name = ""; // 성명
	private String sex = ""; // 주민번호7번째자리번호
	private String sitecode = ""; // DI정보를 위한 사이트 코드

	private String ci = "";
	private String di = "";

	public XPayAuthBean() {
	}

	public XPayAuthBean(byte[] transaction, String step){
		
		//20180615 step 분기 추가
		if (step.equals("STEP1")) {		
			oid			= CommonUtil.toString(transaction,226,64).trim();
			mobileStep	= CommonUtil.toString(transaction,290,5).trim();
			tid			= CommonUtil.toString(transaction,295,24).trim();	
			mobileComm	= CommonUtil.toString(transaction,319,1).trim();
			mobileNum	= CommonUtil.toString(transaction,320,12).trim();
			authMode	= CommonUtil.toString(transaction,332,10).trim();
			authNumber	= CommonUtil.toString(transaction,342,6).trim();
			callBack	= CommonUtil.toString(transaction,348,12).trim();
			mertName	= CommonUtil.toString(transaction,360,20).trim();
			nameCheckYn	= CommonUtil.toString(transaction,380,1).trim();
			buyer		= CommonUtil.toString(transaction,381,15).trim();
			bohoSvnYn	= CommonUtil.toString(transaction,396,1).trim();
			issuerCode	= CommonUtil.toString(transaction,397,4).trim();
			issuerMsg	= CommonUtil.toString(transaction,401,160).trim();
			
			birth		= CommonUtil.toString(transaction,561,6).trim();
			sex			= CommonUtil.toString(transaction,567,1).trim();
			name		= CommonUtil.toString(transaction,568,10).trim();
			sitecode	= CommonUtil.toString(transaction,578,12).trim();
		}
		
		else {
			oid			= CommonUtil.toString(transaction,226,64).trim();
			mobileStep	= CommonUtil.toString(transaction,290,5).trim();
			tid			= CommonUtil.toString(transaction,295,24).trim();	
			mobileComm	= CommonUtil.toString(transaction,319,1).trim();
			mobileNum	= CommonUtil.toString(transaction,320,12).trim();
			authMode	= CommonUtil.toString(transaction,332,10).trim();
			authNumber	= CommonUtil.toString(transaction,342,6).trim();
			callBack	= CommonUtil.toString(transaction,348,12).trim();
			mertName	= CommonUtil.toString(transaction,360,20).trim();
			nameCheckYn	= CommonUtil.toString(transaction,380,1).trim();
			buyer		= CommonUtil.toString(transaction,381,15).trim();
			bohoSvnYn	= CommonUtil.toString(transaction,396,1).trim();
			issuerCode	= CommonUtil.toString(transaction,397,4).trim();
			issuerMsg	= CommonUtil.toString(transaction,401,160).trim();
			
			birth		= CommonUtil.toString(transaction,561,6).trim();
			sex			= CommonUtil.toString(transaction,567,1).trim();
			name		= CommonUtil.toString(transaction,568,10).trim();
			ci			= CommonUtil.toString(transaction,578,90).trim(); // ci 정보 추가
			di			= CommonUtil.toString(transaction,668,70).trim(); // di 정보 추가			
		}
		
	}

	public XPayAuthBean(byte[] transaction) {

		oid = CommonUtil.toString(transaction, 226, 64).trim();
		mobileStep = CommonUtil.toString(transaction, 290, 5).trim();
		tid = CommonUtil.toString(transaction, 295, 24).trim();
		mobileComm = CommonUtil.toString(transaction, 319, 1).trim();
		mobileNum = CommonUtil.toString(transaction, 320, 12).trim();
		authMode = CommonUtil.toString(transaction, 332, 10).trim();
		authNumber = CommonUtil.toString(transaction, 342, 6).trim();
		callBack = CommonUtil.toString(transaction, 348, 12).trim();
		mertName = CommonUtil.toString(transaction, 360, 20).trim();
		nameCheckYn = CommonUtil.toString(transaction, 380, 1).trim();
		buyer = CommonUtil.toString(transaction, 381, 15).trim();
		bohoSvnYn = CommonUtil.toString(transaction, 396, 1).trim();
		issuerCode = CommonUtil.toString(transaction, 397, 4).trim();
		issuerMsg = CommonUtil.toString(transaction, 401, 160).trim();

		birth = CommonUtil.toString(transaction, 561, 6).trim();
		sex = CommonUtil.toString(transaction, 567, 1).trim();
		name = CommonUtil.toString(transaction, 568, 10).trim();
		sitecode = CommonUtil.toString(transaction, 578, 12).trim();

	}

	public XPayAuthBean(String transaction) {
		mciParser(transaction.getBytes(CertCharset.EUC_KR));
	}

	private void mciParser(byte[] transaction) {
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

		makeBirth();
		makeName();
		makeSex();
	}

	public String getTransaction() {
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
		sb.append(CommonUtil.byteFiller(sitecode, 12));

		// sb.append(CommonUtil.byteFiller(customSmsmsg,160));
		// sb.append(CommonUtil.byteFiller(authnumberlength,2));

		return sb.toString();
	}
	
	public String getTransactionNew(String step) {
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
		if (step.equals("STEP1")) {
			sb.append(CommonUtil.byteFiller(sitecode, 12));
		} else {
			sb.append(CommonUtil.byteFiller(ci, 90));
			sb.append(CommonUtil.byteFiller(di, 70));
		}

		// sb.append(CommonUtil.byteFiller(customSmsmsg,160));
		// sb.append(CommonUtil.byteFiller(authnumberlength,2));

		return sb.toString();
	}

	public String getTransactionStep2() {
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

		// sb.append(CommonUtil.byteFiller(customSmsmsg,160));
		// sb.append(CommonUtil.byteFiller(authnumberlength,2));

		return sb.toString();
	}

	public String getTransactionOld(String step) {
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
		if(step.equals("STEP2")) {
			sb.append(CommonUtil.byteFiller(ci, 90));
			sb.append(CommonUtil.byteFiller(di, 70));
		}
		return sb.toString();
	}
	
	public String getTransactionOld2(String step) {
		StringBuffer sb = new StringBuffer();
		sb.append(CommonUtil.byteFiller(oid, 64));
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
		sb.append(CommonUtil.byteFiller(birth, 6));
		sb.append(CommonUtil.byteFiller(sex, 1));
		sb.append(CommonUtil.byteFiller(name, 10));
		sb.append(CommonUtil.byteFiller(ci, 90));
		sb.append(CommonUtil.byteFiller(di, 70));
		return sb.toString();
	}
	
	public String getTransactionOld2() {
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
		sb.append(CommonUtil.byteFiller(ci, 90));
		return sb.toString();
	}

	public String getOid() {
		return oid;
	}

	public void setOid(String oid) {
		this.oid = oid;
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

	public String getBirth() {
		return birth;
	}

	public void setBirth(String birth) {
		this.birth = birth;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSex() {
		return sex;
	}

	public void setSex(String sex) {
		this.sex = sex;
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

	public String makeBirth() {

		String rslt = "";
		if (mobileSsn != null && mobileSsn.length() > 6) {
			rslt = mobileSsn.substring(0, 6);
		}
		birth = rslt;
		return rslt;
	}

	public String makeName() {

		name = buyer;
		return name;
	}

	public String makeSex() {

		String rslt = "";
		if (mobileSsn != null && mobileSsn.length() > 7) {
			rslt = mobileSsn.substring(6, 7);
		}
		sex = rslt;
		return rslt;
	}

	public String getSitecode() {
		return sitecode;
	}

	public void setSitecode(String sitecode) {
		this.sitecode = sitecode;
	}

	public String getCi() {
		return this.ci;
	}

	public void setCi(String ci) {
		this.ci = ci;
	}

	public String getDi() {
		return this.di;
	}

	public void setDi(String di) {
		this.di = di;
	}

}
