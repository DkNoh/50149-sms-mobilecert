package scbk.cert.dao;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import scbk.cert.bean.kcb.KCBCertReqBean;
import scbk.cert.bean.kcb.KCBCertResBean;
import scbk.cert.bean.kcb.KCBHeaderBean;
import scbk.cert.conf.HeaderMsgConstants;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;

public class KCBDAO {

	private String table = "";
	private String table2 = "";

	public KCBDAO(String table, String table2) {
		this.table = table;
		this.table2 = table2;
	}

	/*
	 * 시퀀스를 가져온다.(시퀀스를 가져올 시퀀스 확인 필요!!!
	 */
	public String getSerno() {

		String selectQry = "SELECT NVL(SMS.MBLPHN_SELF_CERT_SEQ.NEXTVAL, 0) AS SEQ FROM DUAL";

		DBFactory db = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rset = null;
		String tran_serno = "";

		try {
			db = DBFactory.getInstance();
			conn = db.getConnection();
			pstmt = conn.prepareStatement(selectQry);
			rset = pstmt.executeQuery();

			while (rset.next()) {
				tran_serno = rset.getString("SEQ");
			}

		} catch (Exception e) {
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=KCBDAO.getSerno", e, this);
		} finally {
			db.close(conn, pstmt, rset);
		}

		return tran_serno;
	}

	/*
	 * insert 처리 한다. // cert_tran_serno, cert_tran_dtm, cert_mode,
	 * tran_rspns_cd, tran_rspns_msg, tran_svc_
	 */

	public boolean insert(String tran_serno, String deptCode, String officeCode, String currentDtm, KCBCertReqBean certInfoBean) {

		String query = "INSERT INTO SMS." + table
						+ " (tran_dt, tran_serno, tran_tm, dept_cd, cert_prgrss_phase_mk"
						+ ", treate_mk, clerk_no, tran_no, mbl_comm_mk, mblphn_no"
						+ ", hldr_ssn, cert_no_sms_snd_flg, cert_no, callback_no, mrchnt_nm"
						+ ", real_nm_cnfrm_ind, nm) " 
					 + "VALUES " 
					 	+ " (?, ?, ?, ?, ?"
					 	+ ", ?, ?, ?, ?, ?" 
					 	+ ", ?, ?, ?, ?, ?" 
					 	+ ", ?, ?) ";

		DBFactory db = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		int cnt = 0;

		try {
			db = DBFactory.getInstance();
			conn = db.getConnection();
			pstmt = conn.prepareStatement(query);

			int idx = 1;

			pstmt.setString(idx++, currentDtm.substring(0, 8));
			pstmt.setInt(idx++, CommonUtil.parseInt(tran_serno));
			pstmt.setString(idx++, currentDtm.substring(8));
			pstmt.setString(idx++, deptCode);
			pstmt.setString(idx++, certInfoBean.getMobileStep());

			/*
			 * 처리진행구분 값 확인해야 함!!!!!! pstmt.setString(idx++, "");
			 */
			pstmt.setString(idx++, "1");
			pstmt.setString(idx++, officeCode);
			pstmt.setString(idx++, certInfoBean.getTid());
			pstmt.setString(idx++, certInfoBean.getMobileComm());
			pstmt.setString(idx++, certInfoBean.getMobileNum());

			pstmt.setString(idx++, certInfoBean.getMobileSsn());
			pstmt.setString(idx++, certInfoBean.getAuthMode());
			pstmt.setString(idx++, certInfoBean.getAuthNumber());
			pstmt.setString(idx++, certInfoBean.getCallBack());
			pstmt.setString(idx++, certInfoBean.getMertName());

			pstmt.setString(idx++, certInfoBean.getNameCheckYn());
			pstmt.setString(idx++, certInfoBean.getBuyer());

			cnt = pstmt.executeUpdate();
			conn.commit();

		} catch (Exception e) {
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=KCBDAO.insert", e, this);
		} finally {
			db.close(pstmt);
			db.close(conn);
		}

		if (cnt > 0) {
			return true;
		} else {
			return false;
		}

	}

	public boolean updateStep1(String tran_serno, String currentDtm, KCBHeaderBean headerBean, KCBCertResBean certResBean) {

		String query = "UPDATE SMS." + table 
					+ " SET   treate_mk = ?"
						+ " , cert_tran_serno = ?"
						+ " , cert_tran_dtm = ?" 
						+ " , cert_mode = ?"
						+ " , tran_rspns_cd = ?" 

						+ " , tran_rspns_msg = ?"
						+ " , tran_svc_rspns_cd = ?" 
						+ " , tran_svc_rspns_msg = ?"
						+ " , real_nm_cnfrm_ind = ?"
					+ " WHERE tran_dt = ? " 
					+ " AND   tran_serno = ?";
		
		String query2 = "INSERT INTO SMS." + table2
				+ " (tran_dt, tran_serno, etc1, etc2) " 
			 + "VALUES " 
			 	+ " (?, ?, ?, ?) ";

		DBFactory db = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		PreparedStatement pstmt2 = null;
		int cnt = 0;

		try {
			db = DBFactory.getInstance();
			conn = db.getConnection();
			pstmt = conn.prepareStatement(query);

			int idx = 1;
			int idx2 = 1;
			Class<HeaderMsgConstants> cls = HeaderMsgConstants.class;
			Field fld = null;
			try {
				fld = cls.getField("HEADER_MSG_" + headerBean.getTranResponseCode());
			} catch (NoSuchFieldException e) {
				fld = cls.getField("HEADER_MSG_M001");
			}
			Object obj = cls.newInstance();

			/*
			 * 처리진행구분 값 확인해야 함!!!!!! 
			 */
			pstmt.setString(idx++, "9");
			pstmt.setInt(idx++, CommonUtil.parseInt(headerBean.getKcbTranSerno().substring(0, 6)));
			pstmt.setString(idx++, headerBean.getKcbTranDtm());
			pstmt.setString(idx++, certResBean.getCertMode());
			pstmt.setString(idx++, headerBean.getTranResponseCode());

			pstmt.setString(idx++, (String) fld.get(obj));
			pstmt.setString(idx++, certResBean.getResponseCode());
			pstmt.setString(idx++, headerBean.getKcbTranSerno() + "|" + certResBean.getResponseMsg().trim());
			pstmt.setString(idx++, certResBean.getPersonalInfoCheckFlag());
			pstmt.setString(idx++, currentDtm.substring(0, 8));
			pstmt.setInt(idx++, CommonUtil.parseInt(tran_serno));

			cnt = pstmt.executeUpdate();
					
			pstmt2 = conn.prepareStatement(query2);
			pstmt2.setString(idx2++, currentDtm.substring(0, 8));
			pstmt2.setInt(idx2++, CommonUtil.parseInt(tran_serno));
			pstmt2.setString(idx2++, certResBean.getCi());
			pstmt2.setString(idx2++, certResBean.getDi());
			pstmt2.executeUpdate();

			conn.commit();
		} catch (Exception e) {
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=KCBDAO.updateStep1", e, this);
		} finally {
			db.close(pstmt);
			db.close(pstmt2);
			db.close(conn);
		}

		if (cnt > 0) {
			return true;
		} else {
			return false;
		}

	}

	public String[] selectCheck(KCBCertReqBean certInfoBean) {

		String query = "SELECT A.CNT AS CNT"
						 + " , B.TRAN_DTM AS CHECK_DT"
						 + " , ROUND((SYSDATE - TO_DATE(B.TRAN_DTM, 'YYYYMMDDHH24MISS'))*10*24*6, 0) AS TIME_CHECK"
					+ " FROM  (SELECT COUNT(1) AS CNT"
						   + " FROM SMS." + table 
						   + " WHERE  TRAN_NO = ?"
						   + " AND    CERT_PRGRSS_PHASE_MK = 'STEP2'"
						   + " AND    TREATE_MK = 'S') A" 
						 + " ,(SELECT TRAN_DTM"
						   + " FROM  (SELECT CONCAT(TRAN_DT, TRAN_TM) AS TRAN_DTM"
						   		  + " FROM   SMS." + table 
						   		  + " WHERE  TRAN_NO = ?"
						   		  + " ORDER BY CONCAT(TRAN_DT, TRAN_TM) ASC)"
						   + " WHERE  ROWNUM = 1) B";

		DBFactory db = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rset = null;
		String[] data = new String[3];

		try {
			db = DBFactory.getInstance();
			conn = db.getConnection();
			pstmt = conn.prepareStatement(query);

			int idx = 1;
			pstmt.setString(idx++, certInfoBean.getTid());// 확인할것.
			pstmt.setString(idx++, certInfoBean.getTid());// 확인할것.

			rset = pstmt.executeQuery();

			while (rset.next()) {
				data[0] = rset.getString("CNT");
				data[1] = CommonUtil.nToB(rset.getString("CHECK_DT"));
				data[2] = CommonUtil.nToB(rset.getString("TIME_CHECK"));
			}

		} catch (Exception e) {
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=KCBDAO.selectCheck", e, this);
		} finally {
			db.close(conn, pstmt, rset);
		}

		return data;
	}

	public boolean selectDeptCode(String deptCode) {

		String query = "SELECT COUNT(*) AS CNT FROM SMS.DEP WHERE DEP_ID='" + deptCode + "' AND ACT_YN='Y' ";

		DBFactory db = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rset = null;
		int cnt = 0;

		try {
			db = DBFactory.getInstance();
			conn = db.getConnection();
			pstmt = conn.prepareStatement(query);
			rset = pstmt.executeQuery();

			while (rset.next()) {
				cnt = rset.getInt("CNT");
			}
		} catch (Exception e) {
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=KCBDAO.selectDeptCode", e, this);
		} finally {
			db.close(conn, pstmt, rset);
		}

		if (cnt > 0) {
			return true;
		} else {
			return false;
		}

	}

}
