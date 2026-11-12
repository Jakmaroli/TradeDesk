package com.tradedesk.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Abstract base class for all orders.
 * Covers Module 2 — Abstract classes, encapsulation.
 *         Module 3 — Inheritance, implements Tradeable.
 * Owner: Member 2
 */
public abstract class Order implements Tradeable, Comparable<Order> {

    private final String orderId;
    private final String username;
    private final String ticker;
    private final int quantity;
    private final OrderType orderType;
    private volatile OrderStatus status;
    private final LocalDateTime createdAt;

    protected Order(String username, String ticker, int quantity, OrderType orderType) {
        this.orderId    = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.username   = username;
        this.ticker     = ticker;
        this.quantity   = quantity;
        this.orderType  = orderType;
        this.status     = OrderStatus.PENDING;
        this.createdAt  = LocalDateTime.now();
    }

    // ── Getters ────────────────────────────────────────────────────────────
    public String getOrderId()         { return orderId; }
    public String getUsername()        { return username; }
    @Override public String getTicker()   { return ticker; }
    @Override public int getQuantity()   { return quantity; }
    @Override public OrderType getOrderType() { return orderType; }
    public OrderStatus getStatus()     { return status; }
    public LocalDateTime getCreatedAt(){ return createdAt; }

    public synchronized void setStatus(OrderStatus status) { this.status = status; }

    /** Template method: subclasses define execution logic. */
    @Override
    public abstract void execute();

    /** Abstract: subclasses return limit price or market price. */
    public abstract double getLimitPrice();

    @Override
    public int compareTo(Order other) {
        // Higher limit price gets priority (buy-side ordering)
        return Double.compare(other.getLimitPrice(), this.getLimitPrice());
    }

    @Override
    public String toString() {
        return String.format("[%s] %s %s x%d @ %.2f — %s",
                orderId, orderType, ticker, quantity, getPrice(), status);
    }
}
