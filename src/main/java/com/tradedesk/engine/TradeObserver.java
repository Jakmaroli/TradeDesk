package com.tradedesk.engine;

import com.tradedesk.model.Order;

/**
 * Observer interface for the Observer design pattern.
 * UI components implement this to receive live trade updates.
 * Covers Module 3 — Interfaces; Design Patterns — Observer.
 * Owner: Member 1 (Jak)
 */
public interface TradeObserver {
    void onOrderFilled(Order order, double executedPrice);
    void onPriceUpdate(String ticker, double newPrice);
    void onOrderRejected(Order order, String reason);

    /**
     * Fired once per settled trade (both sides matched and settled).
     * Default no-op so existing observers (UI, price feed) need no change;
     * the persistence layer overrides this to write the trade to MySQL.
     */
    default void onTrade(Trade trade) { }
}
