package com.tradedesk.db;

import com.tradedesk.engine.Trade;
import com.tradedesk.model.Order;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists submitted orders and executed trades; queries a user's history.
 * Owner: Member 2
 */
public class TradeHistoryDAO {

    /** Simple read-only row for history queries. */
    public record TradeRecord(String orderId, String username, String side, String ticker,
                              int quantity, double price, Timestamp executedAt) {}

    /** Log both sides (buyer + seller) of a matched trade in one transaction. */
    public void insertTrade(Trade t) {
        ConnectionPool pool = null;
        Connection conn = null;
        try {
            pool = ConnectionPool.getInstance();
            conn = pool.borrow();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO trade_history (order_id, username, side, ticker, quantity, price, executed_at) " +
                    "VALUES (?,?,?,?,?,?,?)")) {
                Timestamp ts = Timestamp.valueOf(t.getExecutedAt());
                addRow(ps, t.getBuyOrder().getOrderId(),  t.getBuyer(),  "BUY",  t, ts);
                addRow(ps, t.getSellOrder().getOrderId(), t.getSeller(), "SELL", t, ts);
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseException("insertTrade failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted", e);
        } finally {
            if (pool != null) pool.returnConnection(conn);
        }
    }

    private void addRow(PreparedStatement ps, String orderId, String user, String side,
                        Trade t, Timestamp ts) throws SQLException {
        ps.setString(1, orderId);
        ps.setString(2, user);
        ps.setString(3, side);
        ps.setString(4, t.getTicker());
        ps.setInt(5, t.getQuantity());
        ps.setDouble(6, t.getExecutedPrice());
        ps.setTimestamp(7, ts);
        ps.addBatch();
    }

    /** Record a newly submitted order. */
    public void insertOrder(Order o, String side) {
        ConnectionPool pool = null;
        Connection conn = null;
        try {
            pool = ConnectionPool.getInstance();
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO orders (order_id, username, side, ticker, quantity, order_type, limit_price, status) " +
                    "VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, o.getOrderId());
                ps.setString(2, o.getUsername());
                ps.setString(3, side);
                ps.setString(4, o.getTicker());
                ps.setInt(5, o.getQuantity());
                ps.setString(6, o.getOrderType().name());
                if (o.getOrderType() == com.tradedesk.model.OrderType.LIMIT) ps.setDouble(7, o.getLimitPrice());
                else ps.setNull(7, java.sql.Types.DECIMAL);
                ps.setString(8, o.getStatus().name());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseException("insertOrder failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted", e);
        } finally {
            if (pool != null) pool.returnConnection(conn);
        }
    }

    public void updateOrderStatus(String orderId, String status) {
        ConnectionPool pool = null;
        Connection conn = null;
        try {
            pool = ConnectionPool.getInstance();
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement("UPDATE orders SET status = ? WHERE order_id = ?")) {
                ps.setString(1, status);
                ps.setString(2, orderId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DatabaseException("updateOrderStatus failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted", e);
        } finally {
            if (pool != null) pool.returnConnection(conn);
        }
    }

    /** Most recent trades for a user, newest first. */
    public List<TradeRecord> getHistory(String username, int limit) {
        ConnectionPool pool = null;
        Connection conn = null;
        List<TradeRecord> out = new ArrayList<>();
        try {
            pool = ConnectionPool.getInstance();
            conn = pool.borrow();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT order_id, username, side, ticker, quantity, price, executed_at " +
                    "FROM trade_history WHERE username = ? ORDER BY executed_at DESC, id DESC LIMIT ?")) {
                ps.setString(1, username);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new TradeRecord(rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getString(4), rs.getInt(5), rs.getDouble(6), rs.getTimestamp(7)));
                    }
                }
            }
            return out;
        } catch (SQLException e) {
            throw new DatabaseException("getHistory failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("interrupted", e);
        } finally {
            if (pool != null) pool.returnConnection(conn);
        }
    }
}
