package com.tradedesk.model;

/**
 * Thrown when an order ID cannot be found in the system.
 * Covers Module 4 — Custom Exceptions.
 * Owner: Member 2
 */
public class OrderNotFoundException extends Exception {

    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("Order not found: " + orderId);
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
}
