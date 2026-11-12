package com.tradedesk.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Simple fixed-size JDBC connection pool.
 * Covers Module 5 — BlockingQueue; Module 4 — JDBC.
 * Owner: Member 2
 */
public class ConnectionPool {

    private static ConnectionPool instance;
    private final BlockingQueue<Connection> pool;

    private ConnectionPool() throws SQLException {
        pool = new ArrayBlockingQueue<>(DBConfig.POOL_SIZE);
        for (int i = 0; i < DBConfig.POOL_SIZE; i++) {
            pool.offer(DriverManager.getConnection(DBConfig.URL, DBConfig.USER, DBConfig.PASSWORD));
        }
    }

    public static synchronized ConnectionPool getInstance() throws SQLException {
        if (instance == null) instance = new ConnectionPool();
        return instance;
    }

    public Connection borrow() throws InterruptedException {
        try { return pool.take(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
    }

    public void returnConnection(Connection conn) {
        if (conn != null) pool.offer(conn);
    }
}
