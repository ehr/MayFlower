/**
 *  Copyright 2010 Wallace Wadge
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.jolbox.bonecp;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jsr166y.LinkedTransferQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.jolbox.bonecp.hooks.ConnectionHook;

/**
 * Wrapper around JDBC Statement.
 * 
 * @author wallacew
 * @version $Revision$
 */
public class StatementHandle implements Statement {
	/** For logging purposes - stores parameters to be used for execution. */
	protected Map<Object, Object> logParams;
	/** Set to true if the connection has been "closed". */
	protected AtomicBoolean logicallyClosed = new AtomicBoolean();
	/** A handle to the actual statement. */
	protected Statement internalStatement;
	/** SQL Statement used for this statement. */
	protected String sql;
	/** Cache pertaining to this statement. */
	protected IStatementCache cache;
	/** Handle to the connection holding this statement. */
	protected ConnectionHandle connectionHandle;
	/** The key to use in the cache. */
	private String cacheKey;
	/** If enabled, log all statements being executed. */
	protected boolean logStatementsEnabled;
	/** for logging of addBatch. */
	protected StringBuilder batchSQL;
	/** If true, this statement is in the cache. */
	public volatile boolean inCache = false;
	/** Stack trace capture of where this statement was opened. */
	public String openStackTrace;
	/** Class logger. */
	private static final Log logger = LogFactory.getLog(StatementHandle.class);
	/** Config setting converted to nanoseconds. */
	protected long queryExecuteTimeLimit;
	/** Config setting. */
	protected ConnectionHook connectionHook;
	/** If true, we will close off statements in a separate thread. */
	private boolean statementReleaseHelperEnabled;
	/** Scratch queue of statments awaiting to be closed. */
	private LinkedTransferQueue<StatementHandle> statementsPendingRelease;
	/** An opaque object. */
	private Object debugHandle;

	/**
	 * Constructor to statement handle wrapper.
	 * 
	 * @param internalStatement
	 *            handle to actual statement instance.
	 * @param sql
	 *            statement used for this handle.
	 * @param cache
	 *            Cache handle
	 * @param connectionHandle
	 *            Handle to the connection
	 * @param cacheKey
	 * @param logStatementsEnabled
	 *            set to true to log statements.
	 */
	public StatementHandle(Statement internalStatement, String sql, IStatementCache cache,
			ConnectionHandle connectionHandle, String cacheKey, boolean logStatementsEnabled) {
		this.sql = sql;
		this.internalStatement = internalStatement;
		this.cache = cache;
		this.cacheKey = cacheKey;
		this.connectionHandle = connectionHandle;
		this.logStatementsEnabled = logStatementsEnabled;
		BoneCPConfig config = connectionHandle.getPool().getConfig();
		this.connectionHook = config.getConnectionHook();
		if (this.logStatementsEnabled || this.connectionHook != null) {
			this.logParams = new TreeMap<Object, Object>();
			this.batchSQL = new StringBuilder();
		}

		this.statementReleaseHelperEnabled = connectionHandle.getPool()
				.isStatementReleaseHelperThreadsConfigured();
		if (this.statementReleaseHelperEnabled) {
			this.statementsPendingRelease = connectionHandle.getPool().getStatementsPendingRelease();
		}
		try {

			this.queryExecuteTimeLimit = connectionHandle.getOriginatingPartition()
					.getPreComputedQueryExecuteTimeLimit();
		} catch (Exception e) { // safety!
		// this.connectionHook = null;
			this.queryExecuteTimeLimit = 0;
		}
		// store it in the cache if caching is enabled(unless it's already
		// there). FIXME: make this a direct call to putIfAbsent.
		if (this.cache != null) {
			this.cache.put(this.cacheKey, this);
		}
	}

	/**
	 * Constructor for empty statement (created via connection.createStatement)
	 * 
	 * @param internalStatement
	 *            wrapper to statement
	 * @param connectionHandle
	 *            Handle to the connection that this statement is tied to.
	 * @param logStatementsEnabled
	 *            set to true to enable sql logging.
	 */
	public StatementHandle(Statement internalStatement, ConnectionHandle connectionHandle,
			boolean logStatementsEnabled) {
		this(internalStatement, null, null, connectionHandle, null, logStatementsEnabled);
	}

	/**
	 * Closes off the statement
	 * 
	 * @throws SQLException
	 */
	protected void closeStatement() throws SQLException {
		this.logicallyClosed.set(true);
		if (this.logStatementsEnabled || this.connectionHook != null) {
			this.logParams.clear();
		}
		if (this.cache == null || !this.inCache) { // no cache = throw it away
													// right now
			internalClose();
		}
	}

	/**
	 * Tries to move the item to a waiting consumer. If there's no consumer
	 * waiting, offers the item to the queue if there's space available.
	 * 
	 * @param e
	 *            Item to transfer
	 * @return true if the item was transferred or placed on the queue, false if
	 *         there are no waiting clients and there's no more space on the
	 *         queue.
	 */
	protected boolean tryTransferOffer(StatementHandle e) {
		boolean result = true;
		// if we are using a normal LinkedTransferQueue instead of a bounded
		// one, tryTransfer
		// will never fail.
		if (!this.statementsPendingRelease.tryTransfer(e)) {
			result = this.statementsPendingRelease.offer(e);
		}
		return result;
	}

	public void close() throws SQLException {

		if (this.statementReleaseHelperEnabled) {
			// try moving onto queue so that a separate thread will handle
			// it....
			if (!tryTransferOffer(this)) {
				// closing off the statement if that fails....
				closeStatement();
			}
		} else {
			// otherwise just close it off straight away
			closeStatement();
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#addBatch(java.lang.String)
	 */
	// @Override
	public void addBatch(String sql) throws SQLException {
		checkClosed();
		try {
			if (this.logStatementsEnabled || this.connectionHook != null) {
				this.batchSQL.append(sql);
			}

			this.internalStatement.addBatch(sql);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * Checks if the connection is marked as being logically open and throws an
	 * exception if not.
	 * 
	 * @throws SQLException
	 *             if connection is marked as logically closed.
	 * 
	 * 
	 */
	protected void checkClosed() throws SQLException {
		if (this.logicallyClosed.get()) {
			throw new SQLException("Statement is closed");
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#cancel()
	 */
	// @Override
	public void cancel() throws SQLException {
		checkClosed();
		try {
			this.internalStatement.cancel();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#clearBatch()
	 */
	// @Override
	public void clearBatch() throws SQLException {
		checkClosed();
		try {
			if (this.logStatementsEnabled || this.connectionHook != null) {
				this.batchSQL = new StringBuilder();
			}
			this.internalStatement.clearBatch();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#clearWarnings()
	 */
	// @Override
	public void clearWarnings() throws SQLException {
		checkClosed();
		try {
			this.internalStatement.clearWarnings();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#execute(java.lang.String)
	 */
	// @Override
	public boolean execute(String sql) throws SQLException {
		boolean result = false;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				logger.debug(PoolUtil.fillLogParams(sql, this.logParams));
			}
			long timer = queryTimerStart();
			if (this.connectionHook != null) {
				this.connectionHook
						.onBeforeStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			result = this.internalStatement.execute(sql);
			if (this.connectionHook != null) {
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			queryTimerEnd(sql, timer);

		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;
	}

	/**
	 * Call the onQueryExecuteTimeLimitExceeded hook if necessary
	 * 
	 * @param sql
	 *            sql statement that took too long
	 * @param queryStartTime
	 *            time when query was started.
	 */
	protected void queryTimerEnd(String sql, long queryStartTime) {
		if ((this.queryExecuteTimeLimit != 0) && (this.connectionHook != null)
				&& (System.nanoTime() - queryStartTime) > this.queryExecuteTimeLimit) {
			this.connectionHook.onQueryExecuteTimeLimitExceeded(this.connectionHandle, this, sql,
					this.logParams);
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#execute(java.lang.String, int)
	 */
	// @Override
	public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
		boolean result = false;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				logger.debug(PoolUtil.fillLogParams(sql, this.logParams));
			}

			long queryStartTime = queryTimerStart();
			if (this.connectionHook != null) {
				this.connectionHook
						.onBeforeStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			result = this.internalStatement.execute(sql, autoGeneratedKeys);

			if (this.connectionHook != null) {
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}

			queryTimerEnd(sql, queryStartTime);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * Start off a timer if necessary
	 * 
	 * @return Start time
	 */
	protected long queryTimerStart() {
		return (this.queryExecuteTimeLimit != 0) && (this.connectionHook != null) ? System.nanoTime()
				: Long.MAX_VALUE;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#execute(java.lang.String, int[])
	 */
	// @Override
	public boolean execute(String sql, int[] columnIndexes) throws SQLException {
		boolean result = false;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				logger.debug(PoolUtil.fillLogParams(sql, this.logParams));
			}

			long queryStartTime = queryTimerStart();
			if (this.connectionHook != null) {
				this.connectionHook
						.onBeforeStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}

			result = this.internalStatement.execute(sql, columnIndexes);

			if (this.connectionHook != null) {
				// compiler is smart enough to remove this call if it's a no-op
				// as is the default
				// case with the abstract class
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			queryTimerEnd(sql, queryStartTime);

		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#execute(java.lang.String, java.lang.String[])
	 */
	// @Override
	public boolean execute(String sql, String[] columnNames) throws SQLException {
		boolean result = false;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				logger.debug(PoolUtil.fillLogParams(sql, this.logParams));
			}
			long queryStartTime = queryTimerStart();
			if (this.connectionHook != null) {
				this.connectionHook
						.onBeforeStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			result = this.internalStatement.execute(sql, columnNames);
			if (this.connectionHook != null) {
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}

			queryTimerEnd(sql, queryStartTime);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#executeBatch()
	 */
	// @Override
	public int[] executeBatch() throws SQLException {
		int[] result = null;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				logger.debug(PoolUtil.fillLogParams(this.batchSQL.toString(), this.logParams));
			}
			long queryStartTime = queryTimerStart();
			String query = this.batchSQL == null ? "" : this.batchSQL.toString();
			if (this.connectionHook != null) {
				this.connectionHook.onBeforeStatementExecute(this.connectionHandle, this, query,
						this.logParams);
			}
			result = this.internalStatement.executeBatch();

			if (this.connectionHook != null) {
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, query,
						this.logParams);
			}

			queryTimerEnd(this.batchSQL == null ? "" : this.batchSQL.toString(), queryStartTime);

		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result; // never reached

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#executeQuery(java.lang.String)
	 */
	// @Override
	public ResultSet executeQuery(String sql) throws SQLException {
		ResultSet result = null;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				logger.debug(PoolUtil.fillLogParams(sql, this.logParams));
			}
			long queryStartTime = queryTimerStart();
			if (this.connectionHook != null) {
				this.connectionHook
						.onBeforeStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			result = this.internalStatement.executeQuery(sql);
			if (this.connectionHook != null) {
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}

			queryTimerEnd(sql, queryStartTime);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#executeUpdate(java.lang.String)
	 */
	// @Override
	public int executeUpdate(String sql) throws SQLException {
		int result = 0;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				logger.debug(PoolUtil.fillLogParams(sql, this.logParams));
			}
			long queryStartTime = queryTimerStart();
			if (this.connectionHook != null) {
				this.connectionHook
						.onBeforeStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			result = this.internalStatement.executeUpdate(sql);
			if (this.connectionHook != null) {
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}

			queryTimerEnd(sql, queryStartTime);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#executeUpdate(java.lang.String, int)
	 */
	// @Override
	public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
		int result = 0;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				logger.debug(PoolUtil.fillLogParams(sql, this.logParams));
			}
			long queryStartTime = queryTimerStart();
			if (this.connectionHook != null) {
				this.connectionHook
						.onBeforeStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			result = this.internalStatement.executeUpdate(sql, autoGeneratedKeys);
			if (this.connectionHook != null) {
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}

			queryTimerEnd(sql, queryStartTime);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#executeUpdate(java.lang.String, int[])
	 */
	// @Override
	public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
		int result = 0;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				//logger.debug(PoolUtil.fillLogParams(sql, this.logParams), columnIndexes);
			}
			long queryStartTime = queryTimerStart();
			if (this.connectionHook != null) {
				this.connectionHook
						.onBeforeStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			result = this.internalStatement.executeUpdate(sql, columnIndexes);
			if (this.connectionHook != null) {
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}

			queryTimerEnd(sql, queryStartTime);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#executeUpdate(java.lang.String,
	 *      java.lang.String[])
	 */
	// @Override
	public int executeUpdate(String sql, String[] columnNames) throws SQLException {
		int result = 0;
		checkClosed();
		try {
			if (this.logStatementsEnabled) {
				//logger.debug(PoolUtil.fillLogParams(sql, this.logParams), columnNames);
			}
			long queryStartTime = queryTimerStart();
			if (this.connectionHook != null) {
				this.connectionHook
						.onBeforeStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}
			result = this.internalStatement.executeUpdate(sql, columnNames);
			if (this.connectionHook != null) {
				this.connectionHook.onAfterStatementExecute(this.connectionHandle, this, sql, this.logParams);
			}

			queryTimerEnd(sql, queryStartTime);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);
		}

		return result;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getConnection()
	 */
	// @Override
	public Connection getConnection() throws SQLException {
		checkClosed();
		return this.connectionHandle;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getFetchDirection()
	 */
	// @Override
	public int getFetchDirection() throws SQLException {
		int result = 0;
		checkClosed();
		try {
			result = this.internalStatement.getFetchDirection();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getFetchSize()
	 */
	// @Override
	public int getFetchSize() throws SQLException {
		int result = 0;
		checkClosed();
		try {
			result = this.internalStatement.getFetchSize();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getGeneratedKeys()
	 */
	// @Override
	public ResultSet getGeneratedKeys() throws SQLException {
		ResultSet result = null;
		checkClosed();
		try {
			result = this.internalStatement.getGeneratedKeys();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getMaxFieldSize()
	 */
	// @Override
	public int getMaxFieldSize() throws SQLException {
		int result = 0;
		checkClosed();
		try {
			result = this.internalStatement.getMaxFieldSize();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getMaxRows()
	 */
	// @Override
	public int getMaxRows() throws SQLException {
		int result = 0;
		checkClosed();
		try {
			result = this.internalStatement.getMaxRows();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getMoreResults()
	 */
	// @Override
	public boolean getMoreResults() throws SQLException {
		boolean result = false;
		checkClosed();
		try {
			result = this.internalStatement.getMoreResults();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getMoreResults(int)
	 */
	// @Override
	public boolean getMoreResults(int current) throws SQLException {
		boolean result = false;
		checkClosed();

		try {
			result = this.internalStatement.getMoreResults(current);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getQueryTimeout()
	 */
	// @Override
	public int getQueryTimeout() throws SQLException {
		int result = 0;
		checkClosed();
		try {
			result = this.internalStatement.getQueryTimeout();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getResultSet()
	 */
	// @Override
	public ResultSet getResultSet() throws SQLException {
		ResultSet result = null;
		checkClosed();
		try {
			result = this.internalStatement.getResultSet();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getResultSetConcurrency()
	 */
	// @Override
	public int getResultSetConcurrency() throws SQLException {
		int result = 0;
		checkClosed();
		try {
			result = this.internalStatement.getResultSetConcurrency();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getResultSetHoldability()
	 */
	// @Override
	public int getResultSetHoldability() throws SQLException {
		int result = 0;
		checkClosed();
		try {
			result = this.internalStatement.getResultSetHoldability();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getResultSetType()
	 */
	// @Override
	public int getResultSetType() throws SQLException {
		int result = 0;
		checkClosed();
		try {
			result = this.internalStatement.getResultSetType();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getUpdateCount()
	 */
	// @Override
	public int getUpdateCount() throws SQLException {
		int result = 0;
		checkClosed();
		try {
			result = this.internalStatement.getUpdateCount();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#getWarnings()
	 */
	// @Override
	public SQLWarning getWarnings() throws SQLException {
		SQLWarning result = null;
		checkClosed();
		try {
			result = this.internalStatement.getWarnings();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	// @Override
	/**
	 * Returns true if statement is logically closed
	 * 
	 * @return True if handle is closed
	 */
	public boolean isClosed() {
		return this.logicallyClosed.get();
	}

	// #ifdef JDK6
	public void setPoolable(boolean poolable) throws SQLException {
		checkClosed();
		try {
			this.internalStatement.setPoolable(poolable);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		boolean result = false;
		try {
			result = this.internalStatement.isWrapperFor(iface);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		T result = null;
		try {

			result = this.internalStatement.unwrap(iface);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	public boolean isPoolable() throws SQLException {
		boolean result = false;
		checkClosed();
		try {
			result = this.internalStatement.isPoolable();
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}
		return result;

	}

	// #endif JDK6

	public void setCursorName(String name) throws SQLException {
		checkClosed();
		try {
			this.internalStatement.setCursorName(name);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#setEscapeProcessing(boolean)
	 */
	// @Override
	public void setEscapeProcessing(boolean enable) throws SQLException {
		checkClosed();
		try {
			this.internalStatement.setEscapeProcessing(enable);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#setFetchDirection(int)
	 */
	// @Override
	public void setFetchDirection(int direction) throws SQLException {
		checkClosed();
		try {
			this.internalStatement.setFetchDirection(direction);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#setFetchSize(int)
	 */
	// @Override
	public void setFetchSize(int rows) throws SQLException {
		checkClosed();
		try {
			this.internalStatement.setFetchSize(rows);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#setMaxFieldSize(int)
	 */
	// @Override
	public void setMaxFieldSize(int max) throws SQLException {
		checkClosed();
		try {
			this.internalStatement.setMaxFieldSize(max);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#setMaxRows(int)
	 */
	// @Override
	public void setMaxRows(int max) throws SQLException {
		checkClosed();
		try {
			this.internalStatement.setMaxRows(max);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see java.sql.Statement#setQueryTimeout(int)
	 */
	// @Override
	public void setQueryTimeout(int seconds) throws SQLException {
		checkClosed();
		try {
			this.internalStatement.setQueryTimeout(seconds);
		} catch (Throwable t) {
			throw this.connectionHandle.markPossiblyBroken(t);

		}

	}

	/**
	 * @throws SQLException
	 * 
	 * 
	 */
	protected void internalClose() throws SQLException {
		if (this.logStatementsEnabled || this.connectionHook != null) {
			this.logParams.clear();
			this.batchSQL = new StringBuilder();
		}
		this.internalStatement.close();
	}

	/**
	 * Clears out the cache of statements.
	 */
	protected void clearCache() {
		if (this.cache != null) {
			this.cache.clear();
		}
	}

	/**
	 * Marks this statement as being "open"
	 * 
	 */
	protected void setLogicallyOpen() {
		this.logicallyClosed.set(false);
	}

	@Override
	public String toString() {
		return this.sql;
	}

	/**
	 * Returns the stack trace where this statement was first opened.
	 * 
	 * @return the openStackTrace
	 */
	public String getOpenStackTrace() {
		return this.openStackTrace;
	}

	/**
	 * Sets the stack trace where this statement was first opened.
	 * 
	 * @param openStackTrace
	 *            the openStackTrace to set
	 */
	public void setOpenStackTrace(String openStackTrace) {
		this.openStackTrace = openStackTrace;
	}

	/**
	 * Returns the statement being wrapped around by this wrapper.
	 * 
	 * @return the internalStatement being used.
	 */
	public Statement getInternalStatement() {
		return this.internalStatement;
	}

	/**
	 * Sets the internal statement used by this wrapper.
	 * 
	 * @param internalStatement
	 *            the internalStatement to set
	 */
	public void setInternalStatement(Statement internalStatement) {
		this.internalStatement = internalStatement;
	}

	/**
	 * Sets a debugHandle, an object that is not used by the connection pool at
	 * all but may be set by an application to track this particular connection
	 * handle for any purpose it deems fit.
	 * 
	 * @param debugHandle
	 *            any object.
	 */
	public void setDebugHandle(Object debugHandle) {
		this.debugHandle = debugHandle;
	}

	/**
	 * Returns the debugHandle field.
	 * 
	 * @return debugHandle
	 */
	public Object getDebugHandle() {
		return this.debugHandle;
	}

}