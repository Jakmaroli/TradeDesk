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
}
