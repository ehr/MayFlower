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

import java.sql.SQLException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Periodically sends a keep-alive statement to idle threads and kills off any
 * connections that have been unused for a long time (or broken).
 * 
 * @author wwadge
 * 
 */
public class ConnectionTesterThread implements Runnable {

	/** Connections used less than this time ago are not keep-alive tested. */
	private long idleConnectionTestPeriod;
	/** Max no of ms to wait before a connection that isn't used is killed off. */
	private long idleMaxAge;
	/** Partition being handled. */
	private ConnectionPartition partition;
	/** Scheduler handle. **/
	private ScheduledExecutorService scheduler;
	/** Handle to connection pool. */
	private BoneCP pool;
	/** Logger handle. */
	private static final Log logger = LogFactory.getLog(ConnectionTesterThread.class);

	/**
	 * Constructor
	 * 
	 * @param connectionPartition
	 *            partition to work on
	 * @param scheduler
	 *            Scheduler handler.
	 * @param pool
	 *            pool handle
	 * @param idleMaxAge
	 *            Threads older than this are killed off
	 * @param idleConnectionTestPeriod
	 *            Threads that are idle for more than this time are sent a
	 *            keep-alive.
	 */
	protected ConnectionTesterThread(ConnectionPartition connectionPartition,
			ScheduledExecutorService scheduler, BoneCP pool, long idleMaxAge, long idleConnectionTestPeriod) {
		this.partition = connectionPartition;
		this.scheduler = scheduler;
		this.idleMaxAge = idleMaxAge;
		this.idleConnectionTestPeriod = idleConnectionTestPeriod;
		this.pool = pool;
	}

	/** Invoked periodically. */
	public void run() {
		ConnectionHandle connection = null;
		long tmp;
		try {
			long nextCheck = this.idleConnectionTestPeriod;

			int partitionSize = this.partition.getAvailableConnections();
			long currentTime = System.currentTimeMillis();
			for (int i = 0; i < partitionSize; i++) {

				connection = this.partition.getFreeConnections().poll();
				if (connection != null) {
					connection.setOriginatingPartition(this.partition);
					if (connection.isPossiblyBroken()
							|| ((this.idleMaxAge > 0) && (this.partition.getAvailableConnections() >= this.partition
									.getMinConnections() && System.currentTimeMillis()
									- connection.getConnectionLastUsed() > this.idleMaxAge))) {
						// kill off this connection
						closeConnection(connection);
						continue;
					}

					if (this.idleConnectionTestPeriod > 0
							&& (currentTime - connection.getConnectionLastUsed() > this.idleConnectionTestPeriod)
							&& (currentTime - connection.getConnectionLastReset() > this.idleConnectionTestPeriod)) {
						// send a keep-alive, close off connection if we fail.
						if (!this.pool.isConnectionHandleAlive(connection)) {
							closeConnection(connection);
							continue;
						}
						connection.setConnectionLastReset(System.currentTimeMillis());
						tmp = this.idleConnectionTestPeriod;
					} else {
						tmp = this.idleConnectionTestPeriod
								- (System.currentTimeMillis() - connection.getConnectionLastReset());
					}
					if (tmp < nextCheck) {
						nextCheck = tmp;
					}

					this.pool.putConnectionBackInPartition(connection);

					Thread.sleep(20L); // test slowly, this is not an operation
										// that we're in a hurry to deal with
										// (avoid CPU spikes)...
				}

			} // throw it back on the queue

			this.scheduler.schedule(this, nextCheck, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			if (this.scheduler.isShutdown()) {
				logger.debug("Shutting down connection tester thread.");
			} else {
				logger.error("Connection tester thread interrupted", e);
			}
		}
	}

	/**
	 * Closes off this connection
	 * 
	 * @param connection
	 *            to close
	 */
	private void closeConnection(ConnectionHandle connection) {
		if (connection != null) {
			try {
				connection.internalClose();
			} catch (SQLException e) {
				logger.error("Destroy connection exception", e);
			} finally {
				this.pool.postDestroyConnection(connection);
			}
		}
	}

}
