/*
 * Project Name	:   휴대폰 본인 인증 
 * File Name	:	Gateway.java
 * Date			:	2012-03-01 
 * Version		:	1.0
 * Author		:	TrustMate 02-3446-3730 
 * Comment      :   MCI 로부터 데이터요청을 받아 LG_DACOM 으로 전달하는 주 클래스 
 */

package scbk.cert.main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.ArrayList;

import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.bean.mci.MCIHeaderBean;
import scbk.cert.bean.mci.MCIParser;
import scbk.cert.bean.mci.MCITransBean;
import scbk.cert.bean.uplus.HeaderBean;
import scbk.cert.bean.uplus.XPayAuthBean;
import scbk.cert.conf.ConfigBean;
import scbk.cert.conf.HeaderMsgConstants;
import scbk.cert.conf.KCBConfigBean;
import scbk.cert.dao.DAO;
import scbk.cert.dao.KCBDAO;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;

public class Gateway {

	private ConfigBean configBean = null;
	private KCBConfigBean kcbConfigBean = null;
	private String id = "";
	private MCIHeaderBean mciHeaderBean = null;
	private MCITransBean mciTransBean = null;
	private MCIParser mciParser = null;
	private String deptCode = "";
	private String officeCode = "";

	public Gateway(ConfigBean configBean, KCBConfigBean kcbConfigBean, String id) {
		this.configBean = configBean;
		this.kcbConfigBean = kcbConfigBean;
		this.id = id;
	}

	public byte[] execute(byte[] request) {
		
		// MCI 해더 추출하고. 응답값 셋팅한다.
		mciParser = new MCIParser(request);
		mciHeaderBean = new MCIHeaderBean(mciParser.getSystemHeader());
		mciTransBean = new MCITransBean(mciParser.getTransactionHeader());
		String reqData = CommonUtil.toString(mciParser.getData(), 0, 472);

		
		/* 2021.04.12
		 * MCI 헤더의 tranCd(거래코드)로 LGU+ , KCB 사용 구분 함.
		 * default KCB로 인증하도록 하고 파일 삭제시 아래 try catch 문 삭제.
		 */
		String lguPlus = "../conf/lguPlus";
		String readLine;
		String mobileCertMode = "KCB";
		
		ArrayList<String> lguPlusList = new ArrayList<String>();
		try {
			FileReader fileReader = new FileReader(lguPlus);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			
			while((readLine = bufferedReader.readLine()) != null) {
				lguPlusList.add(readLine);
			}
			if(lguPlusList.indexOf(mciHeaderBean.getTranCd()) >= 0) {
				mobileCertMode = "LGU";
			} else {
				mobileCertMode = "KCB";
			}
			
			bufferedReader.close();
			fileReader.close();
		} catch(Exception e) {
			Log.write("log.root", id + " FILE LOADING ERROR : ../conf/lguPlus ----> USE KCB!!", this);
			// 오류 발생시 무조건 KCB로 인증하도록 함.
			mobileCertMode = "KCB";
		}
		
		/*
		 * DATA 부분 발췌해서 XPAYBEAN 만든다.
		 */
		
		deptCode = reqData.substring(0, 12).trim();
		officeCode = reqData.substring(12, 24).trim();
		reqData = reqData.substring(188);
		
		byte[] response = null;
		if("KCB".equals(mobileCertMode)) {
			Log.write("log.root", id + " KCB CONFIG FILE LOAD CHECK = " + kcbConfigBean.getKcbPort(), this);
			response = kcbCert(reqData);
		} else if("LGU".equals(mobileCertMode)) {
			Log.write("log.root", id + " LGU+ CONFIG FILE LOAD CHECK = " + configBean.getLgdPort(), this);
			response = lguPlusCert(reqData);
		}
		
		return response;
	}

	// MCI 통신 전문구조에서 입력데이터부만 가져온다.
	// 프로그램 해야함.
	public byte[] getResponse(String data, String recvDate, String resultCd, String resultMsg) {
		String resData = CommonUtil.byteFiller(deptCode, 12)
						+ CommonUtil.byteFiller(officeCode, 12) + resultCd
						+ CommonUtil.byteFiller(resultMsg, 160);

		mciHeaderBean.setRqstRspsDvCd("S");
		if (recvDate.equals("")) {
			mciHeaderBean.setTmsgRspsDtm(CommonUtil.getCurrentDate("yyyyMMddHHmmss") + "00"); // 16자리로 변경 필요(1/100초, 2자리)
		} else {
			mciHeaderBean.setTmsgRspsDtm(recvDate + "00");
		}

		mciHeaderBean.setProcRsltDvCd("0"); // 시스템 헤더부 - 처리결과구분코드(0:정상전문,
											// 1:오류전문)

		/*
		 * MCI 응답 전문 생성
		 */
		/*
		 * String conv = "";
		 * if(CommonUtil.toString(mciParser.getConv()).equals(null)){ conv =
		 * CommonUtil.toString(mciParser.getConv()).trim(); }else{ conv =
		 * CommonUtil.toString(mciParser.getConv()); }
		 */
		String conv = CommonUtil.toString(mciParser.getConv()).trim();

		return mciParser.getResponse(mciHeaderBean, mciTransBean, conv, resData, data, resultCd, resultMsg);
	}

	public String check(XPayAuthBean xPayAuthReqBean) {
		// 정상일경우 0000 , 필수값이 없을 경우 XXX0 리턴, 기본값 XXX0 설정

		String checkValue = "XXX0";
		// 기본적으로 step1/2 구분값이 있는지 체크
		if (!xPayAuthReqBean.getMobileStep().equals("")) {
			// step1 or step2 구분이 되어 있는지 체크
			if (xPayAuthReqBean.getMobileStep().equals("STEP1")) {
				// step1 의 필수값은 이통사 구분, 휴대폰 번호, 가입자 주민번호 - 필수값이 없을 경우 XXX0 리턴
				if (!xPayAuthReqBean.getMobileNum().equals("")) {
					if (!xPayAuthReqBean.getMobileSsn().equals("")) {
						checkValue = "0000";
					}
				}
			} else if (xPayAuthReqBean.getMobileStep().equals("STEP2")) {
				// step2 의 필수값은 LG거래번호 , 인증번호 - 필수값이 없을 경우 XXX0 리턴
				// System.out.println("GETTIDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD##############################"+xPayAuthReqBean.getTid());
				// System.out.println("GETTIDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD##############################"+xPayAuthReqBean.getAuthNumber());
				if (!xPayAuthReqBean.getTid().equals("")) {
					if (!xPayAuthReqBean.getAuthNumber().equals("")) {
						checkValue = "0000";
					}
				}
			} else {
				// step 구분값이 없으므로 필수값 부족 코드 리턴
			}

		}
		// return "0000";
		return checkValue; // 수정:2013.02.18(LG U+ 오류??)
	}
	
	public String check(KCBCertReqBean certInfoBean) {
		// 정상일경우 0000 , 필수값이 없을 경우 XXX0 리턴, 기본값 XXX0 설정

		String checkValue = "XXX0";
		
		// 기본적으로 step1/2 구분값이 있는지 체크
		if (!"".equals(certInfoBean.getMobileStep())) {
			// step1 or step2 구분이 되어 있는지 체크
			if ("STEP1".equals(certInfoBean.getMobileStep())) {
				// step1 의 필수값은 이통사 구분, 휴대폰 번호, 가입자 주민번호 - 필수값이 없을 경우 XXX0 리턴
				if (!"".equals(certInfoBean.getMobileNum()) 
						&& !"".equals(certInfoBean.getMobileSsn())
						&& !"".equals(certInfoBean.getMobileComm())) {
					checkValue = "0000";
				}
			} else if ("STEP2".equals(certInfoBean.getMobileStep())) {
				// step2 의 필수값은 거래번호  - 필수값이 없을 경우 XXX0 리턴
				// 인증번호는 Pass 사용시 없을 수 있음.
				if (!"".equals(certInfoBean.getTid())) {
					checkValue = "0000";
				}
			} 
			// 2021.06.29 SCHG 전문 삭제 반영으로 STEP9 사용하지 않음.
//			else if ("STEP9".equals(certInfoBean.getMobileStep())){
//				if (!certInfoBean.getTid().equals("")) {
//					checkValue = "0000";
//				}
//			} 
		}

		return checkValue;
	}
	
	public byte[] getResponse(String data, String recvDate, String resultCd) {
		
		Class<HeaderMsgConstants> cls = HeaderMsgConstants.class;
		Field fld = null;
		String resultMsg = "";
		try {
			try {
				fld = cls.getField("HEADER_MSG_" + resultCd);
			} catch(NoSuchFieldException e) {
				fld = cls.getField("HEADER_MSG_M001");
			}
			Object obj = cls.newInstance();
			resultMsg = (String) fld.get(obj);
		} catch(Exception e) {
			resultMsg = "메시지처리 장애발생";
		}
		String resData = CommonUtil.byteFiller(deptCode, 12) + CommonUtil.byteFiller(officeCode, 12) + resultCd
				+ CommonUtil.byteFiller(resultMsg, 160);

		mciHeaderBean.setRqstRspsDvCd("S");
		if ("".equals(recvDate)) {
			mciHeaderBean.setTmsgRspsDtm(CommonUtil.getCurrentDate("yyyyMMddHHmmss") + "00"); // 16자리로 변경 필요(1/100초,
																								// 2자리)
		} else {
			mciHeaderBean.setTmsgRspsDtm(recvDate + "00");
		}

		mciHeaderBean.setProcRsltDvCd("0"); // 시스템 헤더부 - 처리결과구분코드(0:정상전문, 1:오류전문)

		/*
		 * MCI 응답 전문 생성
		 */
		/*
		 * String conv = ""; if(CommonUtil.toString(mciParser.getConv()).equals(null)){
		 * conv = CommonUtil.toString(mciParser.getConv()).trim(); }else{ conv =
		 * CommonUtil.toString(mciParser.getConv()); }
		 */
		String conv = CommonUtil.toString(mciParser.getConv()).trim();

		return mciParser.getResponse(mciHeaderBean, mciTransBean, conv, resData, data, resultCd, resultMsg);
	}

	private byte[] kcbCert(String reqData) {
		
		KCBCertReqBean certReqBean = new KCBCertReqBean(CommonUtil.setFiller(226) + reqData);
		
		// MCI Response 셋팅을 위해서 설정
		XPayAuthBean xPayAuthReqBean = new XPayAuthBean(CommonUtil.setFiller(226) + reqData);
		XPayAuthBean resXpayAuthBean = new XPayAuthBean();
		
		/*
		 * 테이블명을 셋팅한다.
		 */
		String currentDate = CommonUtil.getCurrentDate("yyyyMM");
		String table = kcbConfigBean.getCertTable() + currentDate;
		String table2 = kcbConfigBean.getCertInfoTable() + currentDate;
		KCBDAO dao = new KCBDAO(table, table2);
		
		/*
		 * 부서코드를 체크한다. 부서코드를 확인하여 부서코드가 존재하지 않을 경우 오류로 리턴한다.
		 */
		if (!dao.selectDeptCode(deptCode)) {
			return getResponse(reqData, "", "XXX4");
		}

		/*
		 * 필수 값을 체크한다. 필수값 체크 시 XXX0 인 경우 필수값 오류로 리턴한다.
		 */
		if ("XXX0".equals(check(certReqBean))) {
			return getResponse(reqData, "", "XXX0");
		}
		
		// 2021.06.29 BSS 전문을 BSX 전문으로 변경.
//		String tranMode = "BSS";
		String tranMode = "BSX";
		/*
		 * STEP2 일 경우 시간 및 횟수 에 대한 결과 처리를 먼저한다. - 초과는 거부한다.
		 */
		if ("STEP2".equals(certReqBean.getMobileStep())) {
			tranMode = "CNSS";
			String[] check = dao.selectCheck(certReqBean);

			// 횟수 체크
			if (CommonUtil.parseInt(check[0]) > 5) {
				return getResponse(reqData, "", "XXX1");
			}

			int time = CommonUtil.parseInt(check[2]);
			// 시간 체크
			if (time >= CommonUtil.parseInt(kcbConfigBean.getTimeOut())) {
				return getResponse(reqData, "", "XXX2");
			}
		} 
		// 2021.06.29 SCHG 전문 삭제 반영으로 STEP9 사용하지 않음.
//		else if ("STEP9".equals(certReqBean.getMobileStep()) && "pass".equals(certReqBean.getAuthMode().toLowerCase())) {
//			tranMode = "SCHG";
//		}
		
		String tranSerno = dao.getSerno();
		String currentDtm = CommonUtil.getCurrentDate("yyyyMMddHHmmss");
		String tranNo = "";
		
		// STEP1일때 거래번호 신규셋팅. 
		if ("STEP1".equals(certReqBean.getMobileStep())) {
			tranNo = "CERT" + currentDtm + CommonUtil.zerofill(tranSerno, 6);
			certReqBean.setTid(tranNo);
		} 
		
		certReqBean.setTranMode(tranMode);
		if (dao.insert(tranSerno, deptCode, officeCode, currentDtm, certReqBean)) {
			/*
			 * 통신진행한다.
			 */
			KCBCommunication comm = new KCBCommunication(kcbConfigBean, id);
			String makeReqData = this.makeReqData(certReqBean, deptCode);
			
			
			byte[] returnData = comm.socketComm(makeReqData, deptCode);
			KCBHeaderBean resHeaderBean = new KCBHeaderBean(returnData);
			KCBCertResBean certResBean = new KCBCertResBean(returnData);
			
			// 2021.05.10 KCB certResBean.getResponseCode() 가 'B000' 일때 '0000'으로 변경
			if("B000".equals(certResBean.getResponseCode())) {
				certResBean.setResponseCode("0000");
			}

//			System.out.println("returnData [" + CommonUtil.toString(returnData) + "]");
			/*
			 * config 테스트 유무에 따라 응답값 설정
			 * // 2021.06.29 SCHG 전문 삭제 반영으로 STEP9 사용하지 않음.
			 */
			if ("Y".equals(kcbConfigBean.getStep1Mode()) && "STEP1".equals(certReqBean.getMobileStep())) {
//					&& ("STEP1".equals(certReqBean.getMobileStep()) || "STEP9".equals(certReqBean.getMobileStep()))) {
				resHeaderBean.setTranResponseCode(kcbConfigBean.getTestRespCode());
				resHeaderBean.setTranResponseMsg(kcbConfigBean.getTestRespMsg());
				certResBean.setResponseCode(kcbConfigBean.getTestRespCode());
				certResBean.setResponseMsg(kcbConfigBean.getTestRespMsg());
				certResBean.setIssuerCode(resHeaderBean.getTranResponseCode());
				certResBean.setIssuerMsg(resHeaderBean.getTranResponseMsg());
			}
			
			if ("Y".equals(kcbConfigBean.getStep2Mode()) && "STEP2".equals(certReqBean.getMobileStep())) {
				resHeaderBean.setTranResponseCode(kcbConfigBean.getTestRespCode());
				resHeaderBean.setTranResponseMsg(kcbConfigBean.getTestRespMsg());
				certResBean.setResponseCode(kcbConfigBean.getTestRespCode());
				certResBean.setResponseMsg(kcbConfigBean.getTestRespMsg());
				certResBean.setIssuerCode(resHeaderBean.getTranResponseCode());
				certResBean.setIssuerMsg(resHeaderBean.getTranResponseMsg());
			}
			
			/*
			 * 결과를 UPDATE 한다. 
			 */
			boolean updated = false;
//			System.out.println("=====>>>> certResBean.getCertMode()=" + certResBean.getCertMode());
//			updated = dao.updateStep1(tranSerno, currentDtm, resHeaderBean, certResBean); // 20231201 개발계 테스트 때문에 return 위로 옮김,  ci,di 셋팅
			
			// STEP1에서 AUTHMODE 에 값이  "PASS"가 아닐때, BSS 전문 신청 후 통신 성공 시 SCHG 전문 바로 전송.
			// 2021.06.29 SCHG 전문 삭제 반영.
//			if("STEP1".equals(certReqBean.getMobileStep()) 
//					&& !"pass".equals(certReqBean.getAuthMode().toLowerCase())
//					&& "0000".equals(certResBean.getResponseCode())
//					&& "P".equals(certResBean.getCertMode())) {
//				String tranSerno2 = dao.getSerno();
//				String currentDtm2 = CommonUtil.getCurrentDate("yyyyMMddHHmmss");
//				
//				KCBHeaderBean headerBean2 = new KCBHeaderBean();
//				headerBean2.setTranCode(kcbConfigBean.getTxCode());
//				headerBean2.setConnMemberCode(kcbConfigBean.getConnMemberCode());
//				headerBean2.setTranKindCode(kcbConfigBean.getTranKindCode());
//				headerBean2.setBizTypeCode(kcbConfigBean.getBizTypeCode());
//				headerBean2.setTranFlag(kcbConfigBean.getTranFlag());
//				headerBean2.setTranSerno(CommonUtil.zerofill(tranSerno2, 6));
//				headerBean2.setTranDtm(currentDtm2);
//				
//				KCBCertReqBean certReqBean2 = new KCBCertReqBean();
//				certReqBean2.setTranMode("SCHG");
//				certReqBean2.setTid(tranNo);
//				certReqBean2.setMobileStep("ST1-2");
//				
//				String makeReqData2 = makeReqData(certReqBean2, deptCode);
//				
//				dao.insert(tranSerno2, deptCode, officeCode, currentDtm2, certReqBean2);
//				KCBCommunication comm2 = new KCBCommunication(kcbConfigBean, id + "_2");
//				
//				byte[] returnData2 = comm2.socketComm(makeReqData2, deptCode);
//				KCBHeaderBean resHeaderBean2 = new KCBHeaderBean(returnData2);
//				KCBCertResBean certResBean2 = new KCBCertResBean(returnData2);
//				
////				System.out.println("SCHG Response===>" + certResBean2.getTransaction());
//				
//				updated = dao.updateStep1(tranSerno2, currentDtm2, resHeaderBean2, certResBean2);
//				
//			} 
			if("STEP1".equals(certReqBean.getMobileStep())) {
				Log.write("log.day", id + " STEP1 CertMode KCB result [" + certResBean.getCertMode() + "]", this);
			}
			resXpayAuthBean.setMobileStep(certReqBean.getMobileStep());
			resXpayAuthBean.setTid(certReqBean.getTid());
			resXpayAuthBean.setMobileComm(certReqBean.getMobileComm());
			resXpayAuthBean.setMobileNum(certReqBean.getMobileNum());
			resXpayAuthBean.setMobileSsn(certReqBean.getMobileSsn());
			resXpayAuthBean.setAuthMode(certReqBean.getAuthMode());
			resXpayAuthBean.setAuthNumber(certReqBean.getAuthNumber());
			resXpayAuthBean.setCallBack(certReqBean.getCallBack());
			resXpayAuthBean.setMertName(certReqBean.getMertName());
			resXpayAuthBean.setNameCheckYn(certReqBean.getNameCheckYn());
			resXpayAuthBean.setBuyer(certReqBean.getBuyer());
			resXpayAuthBean.setBohoSvnYn(certResBean.getCertMode());
			resXpayAuthBean.setIssuerCode(certResBean.getResponseCode());
			resXpayAuthBean.setIssuerMsg(certResBean.getResponseMsg());
			resXpayAuthBean.setCi(certResBean.getCi());
			resXpayAuthBean.setDi(certResBean.getDi());
			
			String serverType = System.getProperty("scbk.cert.server.type", "dev");	
			if("Y".equals(kcbConfigBean.getStep2Mode()) && "STEP2".equals(certReqBean.getMobileStep()) && serverType.equals("dev")) {
                certResBean.setCi("oLmvcJvFsRYSMFhdy4KT8MZX8+gcpjFvdtAxYoo/bB8o9nySOQtgZC4tMy0FgWy4aelOHvqvV2tWM/7NvKjp9Q==");
                certResBean.setDi("MC0GCCqGSIb3DQIJAyEAUdneQn5/5PRLWj4i9KnNYpE6HNC5lR5VURW7eLJOr4Y=");
				resXpayAuthBean.setCi("oLmvcJvFsRYSMFhdy4KT8MZX8+gcpjFvdtAxYoo/bB8o9nySOQtgZC4tMy0FgWy4aelOHvqvV2tWM/7NvKjp9Q==");
                                resXpayAuthBean.setDi("MC0GCCqGSIb3DQIJAyEAUdneQn5/5PRLWj4i9KnNYpE6HNC5lR5VURW7eLJOr4Y=");
			}

            updated = dao.updateStep1(tranSerno, currentDtm, resHeaderBean, certResBean);
//			return getResponse(resXpayAuthBean.getTransactionOld(certReqBean.getMobileStep()), resHeaderBean.getKcbTranDtm()
//					, resHeaderBean.getTranResponseCode(), resHeaderBean.getTranResponseMsg());
			return getResponse(resXpayAuthBean.getTransactionOld(certReqBean.getMobileStep()), resHeaderBean.getKcbTranDtm()
					, certResBean.getResponseCode(), certResBean.getResponseMsg());
			
		} else {
			return getResponse(xPayAuthReqBean.getTransactionOld(certReqBean.getMobileStep()), "", "XXX3");
		}
	}
	
	private byte[] lguPlusCert(String reqData) {
		
		XPayAuthBean xPayAuthReqBean = new XPayAuthBean(CommonUtil.setFiller(226) + reqData);
		
		/*
		 * 테이블명을 셋팅한다.
		 */
		String currentDate = CommonUtil.getCurrentDate("yyyyMM");
		String table = configBean.getCertTable() + currentDate;
		DAO dao = new DAO(table);

		/*
		 * 부서코드를 체크한다. 부서코드를 확인하여 부서코드가 존재하지 않을 경우 오류로 리턴한다.
		 */
		if (!dao.selectDeptCode(deptCode)) {
			return getResponse(reqData, "", "XXX4", "부서코드 미 존재");
		}

		/*
		 * 필수 값을 체크한다. 필수값 체크 시 XXX0 인 경우 필수값 오류로 리턴한다.
		 */
		if (check(xPayAuthReqBean).equals("XXX0")) {
			return getResponse(reqData, "", "XXX0", "필수값 부족");
		}

		/*
		 * STEP2 일 경우 시간 및 횟수 에 대한 결과 처리를 먼저한다. - 초과는 거부한다.
		 */

		if (xPayAuthReqBean.getMobileStep().equals("STEP2")) {
			String[] check = dao.selectCheck(xPayAuthReqBean);
			/*
			 * if(CommonUtil.isNullOrSpace(check[1])){ table =
			 * configBean.getCertTable()+CommonUtil.getOpDate(Calendar.DATE, -1,
			 * currentDate); dao = new DAO(table); check =
			 * dao.selectCheck(xPayAuthReqBean); }
			 */

			// 횟수 체크
			if (CommonUtil.parseInt(check[0]) > 5) {
				return getResponse(reqData, "", "XXX1", "회수 초과");
			}

			int time = CommonUtil.parseInt(check[2]);
			// 시간 체크
			if (time >= CommonUtil.parseInt(configBean.getTimeOut())) {
				return getResponse(reqData, "", "XXX2", "시간 초과");
			}
		}

		/*
		 * INSERT 한다.(pk, deptCode, officeCode , XPayAuthBean) INSERT 실패 시 DB
		 * 오류로 리턴한다.
		 */
		String tranSerno = dao.getSerno();
		if (dao.insert(tranSerno, deptCode, officeCode, xPayAuthReqBean)) {
			/*
			 * 통신진행한다.
			 */
			Communication comm = new Communication(configBean, id);
			// comm.socketComm(reqData);
			// HeaderBean resHeaderBean = comm.getResHeaderBean();
			// XPayAuthBean resXpayAuthBean = comm.getResXpayAuthBean();
			byte[] returnData = comm.socketComm(reqData);
			
			Log.write("log.day", id + " Gateway returnData [" + new String(returnData) + "]", this);
			String step = xPayAuthReqBean.getMobileStep();
			HeaderBean resHeaderBean = new HeaderBean(returnData);
			XPayAuthBean resXpayAuthBean = new XPayAuthBean(returnData, step); 

			/*
			 * config 테스트 유무에 따라 응답값 설정
			 */

			if (configBean.getStep1Mode().equals("Y") && xPayAuthReqBean.getMobileStep().equals("STEP1")) {
				resHeaderBean.setRespCode(configBean.getTestRespCode());
				resHeaderBean.setRespMsg(configBean.getTestRespMsg());
				resXpayAuthBean.setIssuerCode(resHeaderBean.getRespCode());
				resXpayAuthBean.setIssuerMsg(resHeaderBean.getRespMsg());
			}
			if (configBean.getStep2Mode().equals("Y") && xPayAuthReqBean.getMobileStep().equals("STEP2")) {
				resHeaderBean.setRespCode(configBean.getTestRespCode());
				resHeaderBean.setRespMsg(configBean.getTestRespMsg());
				resXpayAuthBean.setIssuerCode(resHeaderBean.getRespCode());
				resXpayAuthBean.setIssuerMsg(resHeaderBean.getRespMsg());
			}

			/*
			 * 결과를 UPDATE 한다. STEP1 / STEP2 에 따라 각기 update 처리한다.
			 */
			boolean updated = false;
			if (xPayAuthReqBean.getMobileStep().equals("STEP1")) {
				updated = dao.updateStep1(tranSerno, resHeaderBean, resXpayAuthBean);
			} else {
				resXpayAuthBean.setTid(xPayAuthReqBean.getTid());
				updated = dao.updateStep1(tranSerno, resHeaderBean, resXpayAuthBean);
			}
			if (!updated) {
				// 로그 남긴다.
			}

			return getResponse(resXpayAuthBean.getTransactionOld(xPayAuthReqBean.getMobileStep()), resHeaderBean.getSendDate()
					, resHeaderBean.getRespCode(), resHeaderBean.getRespMsg());

			/*
			 * 응답데이터 리턴처리
			 */
		} else {
			return getResponse(xPayAuthReqBean.getTransactionOld(xPayAuthReqBean.getMobileStep()), "", "XXX3", "DB INSERT FAIL");
		}
	}
	
	private String makeReqData(KCBCertReqBean certReqBean, String deptCode) {
		String reqHeader = makeReqHeader(certReqBean);
		String reqBody = "";
		// 2021.06.29 BSS 전문을 BSX 전문으로 변경.
//		if("BSS".equals(certReqBean.getTranMode())) {
		if("BSX".equals(certReqBean.getTranMode())) {
			reqBody = makeReqBss(certReqBean, deptCode);
			reqHeader = "0480" + reqHeader;
		} else if("CNSS".equals(certReqBean.getTranMode())) {
			reqBody = makeReqCnss(certReqBean, deptCode);
			reqHeader = "0230" + reqHeader;
		} 
		// 2021.06.29 SCHG 전문 삭제 반영.
//		else if("SCHG".equals(certReqBean.getTranMode())) {
//			reqBody = makeReqSchg(certReqBean, deptCode);
//			reqHeader = "0180" + reqHeader;
//		}
		
//		System.out.println("=======> reqHeader[" + reqHeader + "]");
//		System.out.println("=======> reqBody[" + reqBody + "]");
		return reqHeader + reqBody;
	}
	
	private String makeReqHeader(KCBCertReqBean certReqBean) {
		StringBuffer sb = new StringBuffer();
		sb.append(CommonUtil.byteFiller("ACSEV", 9));
		sb.append(CommonUtil.byteFiller(kcbConfigBean.getConnMemberCode(), 8));
		sb.append(CommonUtil.byteFiller(kcbConfigBean.getTranKindCode(), 4));
		sb.append(CommonUtil.byteFiller(kcbConfigBean.getBizTypeCode(), 3));
		sb.append(CommonUtil.byteFiller(kcbConfigBean.getTranFlag(), 1));
		sb.append(CommonUtil.byteFiller("", 4));	// 응답코드
		sb.append(CommonUtil.byteFiller("", 7));	// KCB 전문 관리번호
		sb.append(CommonUtil.byteFiller("", 14));	// KCB 전문 전송시간
		sb.append(CommonUtil.byteFiller(CommonUtil.zerofill(certReqBean.getTid().substring(18), 7), 7));	// 회원사 전문 관리번호
		sb.append(CommonUtil.byteFiller(certReqBean.getTid().substring(4, 18), 14));	// 회원사 전문전송 시간
		sb.append(CommonUtil.byteFiller("", 16));	// KCB System 정보
		sb.append(CommonUtil.byteFiller("", 43));	// FILLER
		
		return sb.toString();
	}
	
	private String makeReqBss(KCBCertReqBean certReqBean, String deptCode) {
		
		// LGU+의 통신사코드는 (1,2,3,5,6,7) , KCB는 (01,02,03,04,05,06)
		int mobileComm = Integer.parseInt(certReqBean.getMobileComm());
		if(mobileComm > 4) {
			mobileComm = mobileComm - 1;
		}
		
		makeUserInfo(certReqBean);
		StringBuffer sb = new StringBuffer();
		sb.append(CommonUtil.byteFiller(certReqBean.getTranMode(), 4)); // 전문코드 
		sb.append(CommonUtil.byteFiller(kcbConfigBean.getMemberCode(), 12)); // 회원사코드
		sb.append(CommonUtil.byteFiller(certReqBean.getTid(), 30));	// 거래일련번호
		sb.append(CommonUtil.byteFiller("", 4));	// 암호화 키 일련번호
		sb.append(CommonUtil.byteFiller("0000", 4));	// 암호화 알고리즘 코드	
		sb.append(CommonUtil.byteFiller(certReqBean.getMobileNum(), 32));	// 휴대폰번호 [암호화]
		sb.append(CommonUtil.byteFiller(certReqBean.getBuyer(), 96));	// 이름 [암호화]
		sb.append(CommonUtil.byteFiller(certReqBean.getBirth(), 32));	// 생년월일 [암호화]
		sb.append(CommonUtil.byteFiller(certReqBean.getSexCode(), 1));	// 성별구분
		sb.append(CommonUtil.byteFiller(certReqBean.getLocalCode(), 1));	// 내외국인 구분
		sb.append(CommonUtil.byteFiller("0" + mobileComm, 2));	// 통신사구분코드
		sb.append(CommonUtil.byteFiller("Y", 1));	// 개인정보 수집 및 이용 동의
		sb.append(CommonUtil.byteFiller("Y", 1));	// 고유식별정보 처리 동의
		sb.append(CommonUtil.byteFiller("Y", 1));	// 본인확인서비스 이용약관 동의
		sb.append(CommonUtil.byteFiller("Y", 1));	// 통신사 이용약관 동의
		sb.append(CommonUtil.byteFiller("", 14));	// FILLER
		sb.append(CommonUtil.byteFiller(deptCode, 16));	// 채널코드
		sb.append(CommonUtil.byteFiller("0000", 4));	// 메시지구분코드
		sb.append(CommonUtil.byteFiller(kcbConfigBean.getReqUrl(), 50));	// 요청URL명	C	50
		sb.append(CommonUtil.byteFiller(kcbConfigBean.getReqIp(), 16));	// 요청서버 IP
		sb.append(CommonUtil.byteFiller("", 16));	// 고객 IP
		sb.append(CommonUtil.byteFiller("", 12));	// FILLER

		return sb.toString();
	}
	
	private String makeReqCnss(KCBCertReqBean certReqBean, String deptCode) {
		StringBuffer sb = new StringBuffer();
		sb.append(CommonUtil.byteFiller(certReqBean.getTranMode(), 4)); // 전문코드 
		sb.append(CommonUtil.byteFiller(kcbConfigBean.getMemberCode(), 12)); // 회원사코드
		sb.append(CommonUtil.byteFiller(certReqBean.getTid(), 30));	// 거래일련번호
		sb.append(CommonUtil.byteFiller("", 4));	// 암호화 키 일련번호
		sb.append(CommonUtil.byteFiller("0000", 4));	// 암호화 알고리즘 코드	
		sb.append(CommonUtil.byteFiller(certReqBean.getAuthNumber(), 6));	// SMS인증요청번호
		sb.append(CommonUtil.byteFiller("", 40));	// FILLER

		return sb.toString();
	}
	
	// 2021.06.29 SCHG 전문 삭제 반영.
//	private String makeReqSchg(KCBCertReqBean certReqBean, String deptCode) {
//		StringBuffer sb = new StringBuffer();
//		sb.append(CommonUtil.byteFiller(certReqBean.getTranMode(), 4)); // 전문코드 
//		sb.append(CommonUtil.byteFiller(kcbConfigBean.getMemberCode(), 12)); // 회원사코드
//		sb.append(CommonUtil.byteFiller(certReqBean.getTid(), 30));	// 거래일련번호
//		sb.append(CommonUtil.byteFiller("", 4));	// FILLER
//
//		return sb.toString();
//	}
	
	private KCBCertReqBean makeUserInfo(KCBCertReqBean certReqBean) {
		int ssnDiv = Integer.parseInt(certReqBean.getMobileSsn().substring(6,7));
		// 내외국인구분
		if(ssnDiv >= 5 && ssnDiv <= 8) {
			certReqBean.setLocalCode("2");
		} else {
			certReqBean.setLocalCode("1");
		}
		
		// 성별구분
		certReqBean.setSexCode(String.valueOf(ssnDiv % 2));
		
		// 생년월일
		if(ssnDiv == 1 || ssnDiv == 2 || ssnDiv == 5 || ssnDiv == 6) {
			certReqBean.setBirth("19" + certReqBean.getMobileSsn().substring(0,6));
		} else if(ssnDiv == 3 || ssnDiv == 4 || ssnDiv == 7 || ssnDiv == 8) {
			certReqBean.setBirth("20" + certReqBean.getMobileSsn().substring(0,6));
		}
		
		return certReqBean;
	}
	
}
