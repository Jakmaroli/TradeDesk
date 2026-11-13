package com.tradedesk.db;

import com.tradedesk.engine.Trade;
import com.tradedesk.model.MarketOrder;
import com.tradedesk.model.Portfolio;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** Runs only when TRADEDESK_DB_PASSWORD is set and MySQL is up. Cleans up after itself. */
@EnabledIfEnvironmentVariable(named = "TRADEDESK_DB_PASSWORD", matches = ".+")
class DbSmokeTest {

    private static final String U = "smoke_" + System.currentTimeMillis();
    private static final String V = U + "_b";

    @Test
    void fullDatabaseFlow() {
        UserDAO users = new UserDAO();
        PortfolioDAO portfolios = new PortfolioDAO();
        TradeHistoryDAO history = new TradeHistoryDAO();

        assertTrue(users.register(U, "pass1234"));
        assertFalse(users.register(U, "pass1234"), "duplicate must be rejected");
        assertTrue(users.register(V, "pass1234"));
        assertTrue(users.authenticate(U, "pass1234"));
        assertFalse(users.authenticate(U, "wrong"));

        Portfolio p = portfolios.load(U);
        assertNotNull(p);
        assertEquals(DBConfig.STARTING_CASH, p.getCashBalance(), 0.01);
        p.deductCash(5000);
        p.addHolding("TCS", 5, 1000.0);
        portfolios.save(p);

        Portfolio again = portfolios.load(U);
        assertEquals(DBConfig.STARTING_CASH - 5000, again.getCashBalance(), 0.01);
        assertEquals(5, again.getHolding("TCS"));
        assertEquals(1000.0, again.getAvgPrice("TCS"), 0.01);

        MarketOrder buy  = new MarketOrder(U, "TCS", 5);
        MarketOrder sell = new MarketOrder(V, "TCS", 5);
        history.insertOrder(buy, "BUY");
        history.updateOrderStatus(buy.getOrderId(), "FILLED");
        history.insertTrade(new Trade(buy, sell, 4100.0));
        assertEquals(1, history.getHistory(U, 10).size());
        assertEquals("BUY", history.getHistory(U, 10).get(0).side());
        assertEquals("SELL", history.getHistory(V, 10).get(0).side());
    }

    @AfterAll
    static void cleanup() throws Exception {
        ConnectionPool pool = ConnectionPool.getInstance();
        Connection c = pool.borrow();
        try (Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM trade_history WHERE username LIKE 'smoke\\_%'");
            s.executeUpdate("DELETE FROM orders WHERE username LIKE 'smoke\\_%'");
            s.executeUpdate("DELETE FROM users WHERE username LIKE 'smoke\\_%'");
        } finally {
            pool.returnConnection(c);
        }
    }
}
