package com.tradedesk.model;

/**
 * Interface: anything that can be traded.
 * Covers Module 3 — Interfaces.
 * Owner: Member 2
 */
public interface Tradeable {
    String getTicker();
    double getPrice();
    int getQuantity();
    OrderType getOrderType();
    void execute();
}
