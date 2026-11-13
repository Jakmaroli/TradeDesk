package com.tradedesk.db;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Register / authenticate users. Passwords are stored as BCrypt hashes.
 * Owner: Member 2
 */
public class UserDAO {

    /** Creates user + starting portfolio atomically. Returns false if username is taken. */
    public boolean register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.length() < 4) return false;
        ConnectionPool pool = null;
        Connection conn = null;
        try {
            pool = ConnectionPool.getInstance();
            conn = pool.borrow();
            conn.setAutoCommit(false);
            try {
                int userId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, username.trim());
                    ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt(10)));
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        userId = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO portfolios (user_id, cash_balance) VALUES (?, ?)")) {
                    ps.setInt(1, userId);
                    ps.setDouble(2, DBConfig.STARTING_CASH);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                if (e.getErrorCode() == 1062) return false;   // duplicate username
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("register failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted", e);
        } finally {
            if (pool != null) pool.returnConnection(conn);
        }
    }

    /** True if username exists and password matches. */
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        ConnectionPool pool = null;
        Connection conn = null;
        try {
            pool = ConnectionPool.getInstance();
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT password_hash FROM users WHERE username = ?")) {
                ps.setString(1, username.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && BCrypt.checkpw(password, rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("authenticate failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted", e);
        } finally {
            if (pool != null) pool.returnConnection(conn);
        }
    }

    public boolean exists(String username) {
        ConnectionPool pool = null;
        Connection conn = null;
        try {
            pool = ConnectionPool.getInstance();
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        } catch (SQLException e) {
            throw new DatabaseException("exists failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted", e);
        } finally {
            if (pool != null) pool.returnConnection(conn);
        }
    }
}
