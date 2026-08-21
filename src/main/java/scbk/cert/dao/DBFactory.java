package scbk.cert.dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import scbk.cert.conf.DBConfigBean;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;
import scbk.cert.util.XMLFactory;

import com.bitmechanic.sql.ConnectionPool;
import com.bitmechanic.sql.ConnectionPoolManager;

public class DBFactory {

	private static DBConfigBean configBean = null;
	private static DBFactory instance;
	private static ConnectionPoolManager cpm;

	private static int reapConnInterval = 300;
	private static int maxConn = 10;
	private static int idleTimeout = 60;
	private static int checkoutTimeout = 60;
	private static int maxCheckout = 15;

	protected void loadProperties() {
		try {
			if (configBean == null) {
				configBean = (DBConfigBean) XMLFactory.getEntity("DEFAULT_DB");
				maxConn = CommonUtil.parseInt(configBean.getConnection());
			}
		} catch (Exception e) {
			Log.write("log.root", "DBConfigBean Load Error " + e.getMessage(), this);
		}

	}

	public DBFactory() {
		loadProperties();
		if (cpm == null) {
			try {
				Class.forName(configBean.getJdbcDriver());
				cpm = new ConnectionPoolManager(reapConnInterval);
				cpm.addAlias(configBean.getPoolName(), configBean.getJdbcDriver()
						, configBean.getDbUrl(), configBean.getDbUser()
						, configBean.getDbPassword(), maxConn
						, idleTimeout, checkoutTimeout, maxCheckout);
			} catch (Exception e) {
				Log.write("log.root", "DBConfigBean Load Error " + e.getMessage(), this);
			}
		}
	}

	public static DBFactory getInstance() {
		if (instance == null) {
			instance = new DBFactory();
		}
		return instance;
	}

	public Connection getConnection() {
		Connection conn = null;
		try {
			// If we haven't initialized the pool, initialize it
			if (instance == null) {
				DBFactory.getInstance();
			}
			conn = DriverManager.getConnection(ConnectionPoolManager.URL_PREFIX + configBean.getPoolName());

		} catch (SQLException e) {
			Log.write("log.root", "DBConnection Error " + e.getMessage(), this);
		}
		return conn;
	}

	public String dumpPoolInfo() {
		String info = "";
		try {
			ConnectionPool cp = cpm.getPool(configBean.getPoolName());
			info = cp.dumpInfo();
		} catch (Exception e) {
		}

		return info;
	}

	public void close(Connection conn) {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
			}
		}
	}

	public void close(ResultSet rSet) {
		if (rSet != null) {
			try {
				rSet.close();
			} catch (SQLException e) {
			}
		}
	}

	public void close(Statement stmt) {
		if (stmt != null) {
			try {
				stmt.close();
			} catch (SQLException e) {
			}
		}
	}

	public void close(PreparedStatement pstmt) {
		if (pstmt != null) {
			try {
				pstmt.close();
			} catch (SQLException e) {
			}
		}
	}

	public void close(CallableStatement cstmt) {
		if (cstmt != null) {
			try {
				cstmt.close();
			} catch (SQLException e) {
			}
		}
	}

	public void close(Connection conn, PreparedStatement pstmt, ResultSet rset) {
		close(rset);
		close(pstmt);
		close(conn);
	}

	public void close(Connection conn, Statement stmt, ResultSet rset) {
		close(rset);
		close(stmt);
		close(conn);
	}

	public void close(Connection conn, CallableStatement stmt, ResultSet rset) {
		close(rset);
		close(stmt);
		close(conn);
	}

	public int preparedExecuteUpdate(String query) throws SQLException {
		PreparedStatement pstmt = null;
		Connection conn = null;
		int result = 0;

		try {
			conn = getConnection();
			pstmt = conn.prepareStatement(query);
			result = pstmt.executeUpdate();
			conn.commit();
		} catch (SQLException t) {
			throw t;
		} finally {
			close(pstmt);
			close(conn);
		}
		return result;
	}

	public int statementExecuteUpdate(String query) throws SQLException {
		Statement stmt = null;
		Connection conn = null;
		int result = 0;

		try {
			conn = getConnection();
			stmt = conn.createStatement();
			result = stmt.executeUpdate(query);
			conn.commit();
		} catch (SQLException t) {
			throw t;
		} finally {
			close(stmt);
			close(conn);
		}
		return result;
	}
}
