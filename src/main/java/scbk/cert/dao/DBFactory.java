package scbk.cert.dao;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import scbk.cert.conf.DBConfigBean;
import scbk.cert.util.CommonUtil;
import scbk.cert.util.Log;
import scbk.cert.util.XMLFactory;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

public class DBFactory {

	private static DBConfigBean configBean = null;
	private static DBFactory instance;
	private static volatile HikariDataSource dataSource;
	private static int maxConn = 10;
	private static int idleTimeout = 60;

	protected void loadProperties() {
		try {
			if (configBean == null) {
				configBean = (DBConfigBean) XMLFactory.getEntity("DEFAULT_DB");
				maxConn = CommonUtil.parseInt(configBean.getConnection());
			}
		} catch (Exception e) {
			Log.error("log.root", "event=DB_CONFIG_LOAD_FAILED", e, this);
		}

	}

	public DBFactory() {
		synchronized (DBFactory.class) {
			loadProperties();
			if (dataSource == null) {
				try {
					dataSource = createDataSource(configBean);
				} catch (Exception e) {
					Log.error("log.root", "event=DB_POOL_INITIALIZATION_FAILED", e, this);
				}
			}
		}
	}

	static HikariDataSource createDataSource(DBConfigBean bean) {
		// No-arg construction preserves lazy database connection establishment.
		HikariDataSource pool = new HikariDataSource();
		pool.setPoolName(bean.getPoolName());
		pool.setDriverClassName(bean.getJdbcDriver());
		pool.setJdbcUrl(bean.getDbUrl());
		pool.setUsername(bean.getDbUser());
		pool.setPassword(bean.getDbPassword());
		int configuredMax = CommonUtil.parseInt(bean.getConnection());
		pool.setMaximumPoolSize(configuredMax > 0 ? configuredMax : maxConn);
		pool.setMinimumIdle(0);
		pool.setIdleTimeout(idleTimeout * 1000L);
		pool.setMaxLifetime(0);
		pool.setKeepaliveTime(0);
		// Legacy checkoutTimeout was borrowed-connection age, NOT borrow wait time.
		// This is a new, bounded acquisition wait; see the migration report.
		pool.setConnectionTimeout(Long.parseLong(System.getProperty(
				"scbk.cert.db.connectionTimeoutMs", "60000")));
		pool.setInitializationFailTimeout(1);
		pool.setLeakDetectionThreshold(60000);
		// jdbcpool 0.99 preserves the driver's default auto-commit=true.
		pool.setAutoCommit(true);
		// Preserve ojdbc14-era explicit commit() calls while auto-commit is enabled.
		pool.addDataSourceProperty("oracle.jdbc.autoCommitSpecCompliant", "false");
		return pool;
	}

	public static synchronized DBFactory getInstance() {
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
			HikariDataSource pool = dataSource;
			if (pool == null) {
				throw new SQLException("Database pool is not initialized");
			}
			conn = pool.getConnection();

		} catch (SQLException | RuntimeException e) {
			Log.error("log.root", "event=DB_CONNECTION_ACQUIRE_FAILED", e, this);
		}
		return conn;
	}

	public String dumpPoolInfo() {
		String info = "";
		try {
			HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
			if (pool != null) {
				info = "active=" + pool.getActiveConnections()
						+ ", idle=" + pool.getIdleConnections()
						+ ", total=" + pool.getTotalConnections()
						+ ", waiting=" + pool.getThreadsAwaitingConnection();
			}
		} catch (Exception e) {
		}

		return info;
	}

	public static synchronized void shutdown() {
		// Do not call getInstance(): shutdown must not load config or start a pool.
		if (dataSource != null) {
			dataSource.close();
		}
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
