package com.tradedesk.model;

/**
 * Market order — executes immediately at current market price.
 * Covers Module 3 — Inheritance, method overriding.
 * Owner: Member 2
 */
public class MarketOrder extends Order {

    private double executionPrice;

    public MarketOrder(String username, String ticker, int quantity) {
        super(username, ticker, quantity, OrderType.MARKET);
    }

    public void setExecutionPrice(double price) { this.executionPrice = price; }

    @Override
    public double getPrice() { return executionPrice; }

    @Override
    public double getLimitPrice() { return Double.MAX_VALUE; } // market always goes first

    @Override
    public void execute() {
        if (executionPrice <= 0) throw new IllegalStateException("Execution price not set");
        setStatus(OrderStatus.FILLED);
    }
}
