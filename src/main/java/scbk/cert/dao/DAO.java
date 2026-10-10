package scbk.cert.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import scbk.cert.bean.uplus.HeaderBean;
import scbk.cert.bean.uplus.XPayAuthBean;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;

public class DAO {

	private String table = "";
	private long tran_serno = 0;

	public DAO(String table) {
		this.table = table;
	}

	/*
	 * 시퀀스를 가져온다.(시퀀스를 가져올 시퀀스 확인 필요!!!
	 */
	public String getSerno() {

		String selectQry = "SELECT NVL(SMS.MBLPHN_SELF_CERT_SEQ.NEXTVAL,0) AS SEQ FROM DUAL";

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
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=DAO.getSerno", e, this);
		} finally {
			db.close(conn, pstmt, rset);
		}

		return tran_serno;
	}

	/*
	 * insert 처리 한다.
	 */

	public boolean insert(String tran_serno, String deptCode, String officeCode, XPayAuthBean xPayAuthBean) {

		String query = "INSERT INTO SMS." + table
				+ " (tran_dt, tran_serno, dept_cd, cert_prgrss_phase_mk, treate_mk, clerk_no, "
				+ "lg_u_plus_tran_no, mbl_comm_mk, mblphn_no, hldr_ssn, cert_no_sms_snd_flg, cert_no, callback_no, "
				+ "mrchnt_nm, real_nm_cnfrm_ind, nm, prtct_srvc_use_ind, authnt_req_tm) "
				/*
				 * 인증응답시간, 인증LG 응답코드/메세지, 인증 이통사 응답코드/메세지는 Insert 가 되지 않는 필드임으로
				 * 제거 처리
				 */
				// +
				// "authnt_rspns_tm, authnt_lg_rspns_cd, authnt_lg_rspns_msg, authnt_mbl_comm_rspns_cd, authnt_mbl_comm_rspns_msg) "
				// + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
				+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";

		DBFactory db = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		int cnt = 0;

		try {
			db = DBFactory.getInstance();
			conn = db.getConnection();
			pstmt = conn.prepareStatement(query);

			int idx = 1;

			pstmt.setString(idx++, CommonUtil.getCurrentDate("yyyyMMdd"));
			pstmt.setInt(idx++, CommonUtil.parseInt(tran_serno));
			pstmt.setString(idx++, deptCode);
			pstmt.setString(idx++, xPayAuthBean.getMobileStep());
			/*
			 * 처리진행구분 값 확인해야 함!!!!!! pstmt.setString(idx++, "");
			 */
			pstmt.setString(idx++, "1");
			pstmt.setString(idx++, officeCode);
			pstmt.setString(idx++, xPayAuthBean.getTid());
			pstmt.setString(idx++, xPayAuthBean.getMobileComm());
			pstmt.setString(idx++, xPayAuthBean.getMobileNum());
			pstmt.setString(idx++, xPayAuthBean.getMobileSsn());
			pstmt.setString(idx++, xPayAuthBean.getAuthMode());
			pstmt.setString(idx++, xPayAuthBean.getAuthNumber());
			pstmt.setString(idx++, xPayAuthBean.getCallBack());
			pstmt.setString(idx++, xPayAuthBean.getMertName());
			pstmt.setString(idx++, xPayAuthBean.getNameCheckYn());
			pstmt.setString(idx++, xPayAuthBean.getBuyer());
			pstmt.setString(idx++, xPayAuthBean.getBohoSvnYn());
			pstmt.setString(idx++, CommonUtil.getCurrentDate("yyyyMMddHHmmss"));

			cnt = pstmt.executeUpdate();
			conn.commit();

		} catch (Exception e) {
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=DAO.insert", e, this);
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

	public boolean updateStep1(String tran_serno, HeaderBean headerBean,
			XPayAuthBean xPayAuthBean) {

		String query = "UPDATE SMS." + table
				+ " SET treate_mk=?, lg_u_plus_tran_no=?, mbl_comm_mk=?, authnt_rspns_tm=?, authnt_lg_rspns_cd=?, "
				+ "authnt_lg_rspns_msg=?, authnt_mbl_comm_rspns_cd=?, authnt_mbl_comm_rspns_msg=?  "
				+ "WHERE tran_serno=? ";

		DBFactory db = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		int cnt = 0;

		try {
			db = DBFactory.getInstance();
			conn = db.getConnection();
			pstmt = conn.prepareStatement(query);

			int idx = 1;

			/*
			 * 처리진행구분 값 확인해야 함!!!!!! pstmt.setString(idx++, "");
			 */
			pstmt.setString(idx++, "9");
			pstmt.setString(idx++, xPayAuthBean.getTid());
			pstmt.setString(idx++, xPayAuthBean.getMobileComm());
			pstmt.setString(idx++, headerBean.getSendDate());
			pstmt.setString(idx++, headerBean.getRespCode());
			pstmt.setString(idx++, headerBean.getRespMsg());
			pstmt.setString(idx++, xPayAuthBean.getIssuerCode());
			pstmt.setString(idx++, xPayAuthBean.getIssuerMsg());
			pstmt.setInt(idx++, CommonUtil.parseInt(tran_serno));

			cnt = pstmt.executeUpdate();
			conn.commit();

		} catch (Exception e) {
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=DAO.updateStep1", e, this);
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

	public boolean updateStep2(String tran_serno, HeaderBean headerBean,
			XPayAuthBean xPayAuthBean) {

		String query = "UPDATE SMS." + table
				+ " SET treate_mk=?, mbl_comm_mk=?, authnt_rspns_tm=?, authnt_lg_rspns_cd=?, "
				+ "authnt_lg_rspns_msg=?, authnt_mbl_comm_rspns_cd=?, authnt_mbl_comm_rspns_msg=?  "
				+ "WHERE lg_u_plus_tran_no=? AND tran_serno=? ";

		DBFactory db = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		int cnt = 0;

		try {
			db = DBFactory.getInstance();
			conn = db.getConnection();
			pstmt = conn.prepareStatement(query);

			int idx = 1;

			/*
			 * 처리진행구분 값 확인해야 함!!!!!! pstmt.setString(idx++, "");
			 */
			pstmt.setString(idx++, "9");
			pstmt.setString(idx++, xPayAuthBean.getMobileComm());
			pstmt.setString(idx++, headerBean.getSendDate());
			pstmt.setString(idx++, headerBean.getRespCode());
			pstmt.setString(idx++, headerBean.getRespMsg());
			pstmt.setString(idx++, xPayAuthBean.getIssuerCode());
			pstmt.setString(idx++, xPayAuthBean.getIssuerMsg());
			pstmt.setString(idx++, xPayAuthBean.getTid());
			pstmt.setInt(idx++, CommonUtil.parseInt(tran_serno));

			cnt = pstmt.executeUpdate();
			conn.commit();

		} catch (Exception e) {
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=DAO.updateStep2", e, this);
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

	public String[] selectCheck(XPayAuthBean xPayAuthBean) {
		/*
		 * String query =
		 * "SELECT A.CNT AS CNT,b.authnt_req_tm,(TO_DATE(b.authnt_req_tm,'YYYYMMDDHH24MISS') -  SYSDATE ) /10/24/6 AS CHECK_DT  "
		 * //
		 * "    TO_CHAR(TO_DATE(b.authnt_req_tm,'YYYYMMDDHH24MISS'),' AS SEND_DT, TO_CHAR(SYSDATE  - "
		 * +minutes+"/10/24/6,'YYYYMMDDHH24MISS') AS CHECK_DT " +
		 * "FROM (SELECT COUNT(*) AS CNT FROM "
		 * +table+" WHERE lg_u_plus_tran_no = ? ) A, " +
		 * "(SELECT authnt_req_tm FROM (SELECT authnt_req_tm FROM "+table+
		 * " WHERE lg_u_plus_tran_no = ? ORDER BY authnt_req_tm ASC) rownum=1 ) B "
		 * ;
		 */

		String query = "SELECT A.CNT AS CNT, b.authnt_req_tm AS CHECK_DT, ROUND((SYSDATE - TO_DATE(b.authnt_req_tm,'YYYYMMDDHH24MISS'))*10*24*6,0) AS TIME_CHECK "
				+ "FROM (SELECT COUNT(*) AS CNT FROM SMS."
				+ table
				+ " WHERE lg_u_plus_tran_no = '"
				+ xPayAuthBean.getTid()
				+ "' ) A, "
				+ "(SELECT authnt_req_tm FROM (SELECT authnt_req_tm FROM SMS."
				+ table
				+ " WHERE lg_u_plus_tran_no = '"
				+ xPayAuthBean.getTid()
				+ "' ORDER BY authnt_req_tm ASC) WHERE rownum=1 ) B ";

		DBFactory db = null;
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rset = null;
		String[] data = new String[3];

		try {
			db = DBFactory.getInstance();
			conn = db.getConnection();
			pstmt = conn.prepareStatement(query);
			rset = pstmt.executeQuery();

			while (rset.next()) {
				data[0] = rset.getString("CNT");
				data[1] = CommonUtil.nToB(rset.getString("CHECK_DT"));
				data[2] = CommonUtil.nToB(rset.getString("TIME_CHECK"));
			}

		} catch (Exception e) {
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=DAO.selectCheck", e, this);
		} finally {
			db.close(conn, pstmt, rset);
		}

		return data;
	}

	public boolean selectDeptCode(String deptCode) {

		String query = "SELECT COUNT(*) AS CNT FROM SMS.DEP WHERE DEP_ID='"
				+ deptCode + "' AND ACT_YN='Y' ";

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
			Log.error("log.day", "event=DB_OPERATION_FAILED operation=DAO.selectDeptCode", e, this);
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
