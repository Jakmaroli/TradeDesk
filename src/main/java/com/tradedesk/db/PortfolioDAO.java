package com.tradedesk.db;

import com.tradedesk.model.Portfolio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * Load / save a user's Portfolio (cash + holdings).
 * Owner: Member 2
 */
public class PortfolioDAO {

    /** Returns the saved portfolio, or null if the user doesn't exist. */
    public Portfolio load(String username) {
        ConnectionPool pool = null;
        Connection conn = null;
        try {
            pool = ConnectionPool.getInstance();
            conn = pool.borrow();
            int userId;
            Portfolio p;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT u.id, pf.cash_balance FROM users u " +
                    "JOIN portfolios pf ON pf.user_id = u.id WHERE u.username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    userId = rs.getInt(1);
                    p = new Portfolio(username, rs.getDouble(2));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ticker, quantity, avg_buy_price FROM holdings WHERE user_id = ?")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) p.addHolding(rs.getString(1), rs.getInt(2), rs.getDouble(3));
                }
            }
            return p;
        } catch (SQLException e) {
            throw new DatabaseException("load portfolio failed for " + username, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted", e);
        } finally {
            if (pool != null) pool.returnConnection(conn);
        }
    }

    /** Saves cash and replaces all holdings in one transaction. User must already exist. */
    public void save(Portfolio p) {
        ConnectionPool pool = null;
        Connection conn = null;
        try {
            pool = ConnectionPool.getInstance();
            conn = pool.borrow();
            conn.setAutoCommit(false);
            try {
                int userId = userId(conn, p.getUsername());
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO portfolios (user_id, cash_balance) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE cash_balance = VALUES(cash_balance)")) {
                    ps.setInt(1, userId);
                    ps.setDouble(2, p.getCashBalance());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM holdings WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }
                Map<String, Integer> holdings = p.getHoldings();
                if (!holdings.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO holdings (user_id, ticker, quantity, avg_buy_price) VALUES (?,?,?,?)")) {
                        for (Map.Entry<String, Integer> e : holdings.entrySet()) {
                            ps.setInt(1, userId);
                            ps.setString(2, e.getKey());
                            ps.setInt(3, e.getValue());
                            ps.setDouble(4, p.getAvgPrice(e.getKey()));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("save portfolio failed for " + p.getUsername(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted", e);
        } finally {
            if (pool != null) pool.returnConnection(conn);
        }
    }

    private int userId(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Unknown user: " + username);
                return rs.getInt(1);
            }
        }
    }
}
