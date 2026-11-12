package com.tradedesk.engine;

import com.tradedesk.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for the matching engine and order book.
 * Covers Module 4 — Exception handling; Module 5 — Thread safety.
 * Owner: Member 1 (Jak)
 */
@DisplayName("MatchingEngine Tests")
class MatchingEngineTest {

    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        // Use seed=42 for deterministic price movements in tests
        engine = new MatchingEngine(new RandomWalkStrategy(42));
    }

    // ── Order Book ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Market buy order gets filled against limit sell")
    void testMarketBuyFilledAgainstLimitSell() throws Exception {
        engine.getOrCreatePortfolio("buyer");
        engine.getOrCreatePortfolio("seller");

        // Give seller some shares
        engine.getOrCreatePortfolio("seller").addHolding("RELIANCE", 10, 2800.0);

        // Place a limit sell at 2850
        LimitOrder sell = new LimitOrder("SELL:seller", "RELIANCE", 10, 2850.00);
        engine.getOrderBooks().get("RELIANCE").addSellOrder(sell);

        // Place market buy
        MarketOrder buy = new MarketOrder("buyer", "RELIANCE", 10);
        buy.setExecutionPrice(2860.00); // above limit → should match
        engine.getOrderBooks().get("RELIANCE").addBuyOrder(buy);

        Optional<Trade> trade = engine.getOrderBooks().get("RELIANCE").tryMatch();
        assertTrue(trade.isPresent(), "Should produce a trade");
        assertEquals("RELIANCE", trade.get().getTicker());
        assertEquals(10, trade.get().getQuantity());
    }

    @Test
    @DisplayName("Limit buy below limit sell does NOT match")
    void testLimitBuyBelowSellDoesNotMatch() {
        LimitOrder buy  = new LimitOrder("buyer",         "TCS", 5, 4000.0);
        LimitOrder sell = new LimitOrder("SELL:seller",   "TCS", 5, 4200.0);

        OrderBook book = engine.getOrderBooks().get("TCS");
        book.addBuyOrder(buy);
        book.addSellOrder(sell);

        Optional<Trade> trade = book.tryMatch();
        assertFalse(trade.isPresent(), "No match when buy price < sell price");
    }

    // ── Custom Exceptions ──────────────────────────────────────────────────

    @Test
    @DisplayName("InvalidTickerException thrown for unknown ticker")
    void testInvalidTickerThrows() {
        engine.getOrCreatePortfolio("user1");
        assertThrows(InvalidTickerException.class,
                () -> engine.submitMarketBuy("user1", "FAKECOIN", 1));
    }

    @Test
    @DisplayName("InsufficientFundsException thrown when cash is too low")
    void testInsufficientFundsThrows() {
        // Portfolio starts with ₹10 lakh; try to buy 1000 shares of MARUTI @ ~12400
        // → cost ≈ ₹1.24 cr → should fail
        engine.getOrCreatePortfolio("poorTrader");
        assertThrows(InsufficientFundsException.class,
                () -> engine.submitLimitBuy("poorTrader", "MARUTI", 1000, 12400.0));
    }

    // ── Portfolio ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Portfolio starts with ₹10 lakh cash")
    void testInitialPortfolioBalance() {
        Portfolio pf = engine.getOrCreatePortfolio("newUser");
        assertEquals(1_000_000.0, pf.getCashBalance(), 0.01);
    }

    @Test
    @DisplayName("Portfolio holding added correctly")
    void testPortfolioHolding() {
        Portfolio pf = engine.getOrCreatePortfolio("inv1");
        pf.addHolding("INFOSYS", 10, 1430.0);
        assertEquals(10, pf.getHolding("INFOSYS"));
        assertEquals(1430.0, pf.getAvgPrice("INFOSYS"), 0.01);
    }

    @Test
    @DisplayName("Average cost calculation is correct after two buys")
    void testAverageCostCalc() {
        Portfolio pf = engine.getOrCreatePortfolio("avgTest");
        pf.addHolding("WIPRO", 10, 500.0); // 10 @ 500
        pf.addHolding("WIPRO", 10, 600.0); // 10 @ 600 → avg = 550
        assertEquals(550.0, pf.getAvgPrice("WIPRO"), 0.01);
    }

    @Test
    @DisplayName("Selling more than held throws exception")
    void testOversellThrows() {
        Portfolio pf = engine.getOrCreatePortfolio("over");
        pf.addHolding("TCS", 5, 4100.0);
        assertThrows(IllegalStateException.class, () -> pf.removeHolding("TCS", 10));
    }

    // ── Leaderboard / Streams ──────────────────────────────────────────────

    @Test
    @DisplayName("Leaderboard is sorted descending by portfolio value")
    void testLeaderboardOrder() {
        Portfolio p1 = engine.getOrCreatePortfolio("alice");
        Portfolio p2 = engine.getOrCreatePortfolio("bob");
        // alice has more cash (no changes)
        // just verify the list is non-empty and sorted
        List<Map.Entry<String, Double>> board = engine.getLeaderboard();
        assertFalse(board.isEmpty());
        for (int i = 0; i < board.size() - 1; i++) {
            assertTrue(board.get(i).getValue() >= board.get(i + 1).getValue(),
                    "Leaderboard must be sorted descending");
        }
    }

    // ── Enums ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OrderStatus terminal check works correctly")
    void testOrderStatusTerminal() {
        assertTrue(OrderStatus.FILLED.isTerminal());
        assertTrue(OrderStatus.CANCELLED.isTerminal());
        assertFalse(OrderStatus.PENDING.isTerminal());
        assertFalse(OrderStatus.PARTIALLY_FILLED.isTerminal());
    }

    // ── Parameterised test ─────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"RELIANCE", "TCS", "INFOSYS", "HDFCBANK", "WIPRO"})
    @DisplayName("All major tickers are listed in the engine")
    void testKnownTickersPresent(String ticker) {
        assertNotNull(engine.getStocks().get(ticker),
                "Ticker " + ticker + " should be in the engine");
    }

    // ── LimitOrder validation ──────────────────────────────────────────────

    @Test
    @DisplayName("LimitOrder with negative price throws IllegalArgumentException")
    void testLimitOrderNegativePrice() {
        assertThrows(IllegalArgumentException.class,
                () -> new LimitOrder("user", "TCS", 5, -1.0));
    }

    // ── Concurrency (basic) ────────────────────────────────────────────────

    @Test
    @DisplayName("Order book is safe under concurrent additions")
    void testConcurrentOrderAdditions() throws InterruptedException {
        OrderBook book = engine.getOrderBooks().get("INFOSYS");
        int threads = 10;
        Thread[] workers = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            workers[i] = new Thread(() -> {
                LimitOrder order = new LimitOrder("user" + idx, "INFOSYS", 1, 1430.0 + idx);
                book.addBuyOrder(order);
            });
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();

        assertEquals(threads, book.getBuyDepth(), "All orders should be in the book");
    }
}
