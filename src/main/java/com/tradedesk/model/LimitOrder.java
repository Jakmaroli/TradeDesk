package com.tradedesk.model;

/**
 * Limit order — executes only when price meets the limit.
 * Covers Module 3 — Inheritance, method overriding.
 * Owner: Member 2
 */
public class LimitOrder extends Order {

    private final double limitPrice;

    public LimitOrder(String username, String ticker, int quantity, double limitPrice) {
        super(username, ticker, quantity, OrderType.LIMIT);
        if (limitPrice <= 0) throw new IllegalArgumentException("Limit price must be positive");
        this.limitPrice = limitPrice;
    }

    @Override
    public double getPrice()      { return limitPrice; }

    @Override
    public double getLimitPrice() { return limitPrice; }

    @Override
    public void execute() {
        setStatus(OrderStatus.FILLED);
    }

    public boolean isFillable(double marketPrice) {
        return marketPrice <= limitPrice;
    }
}
