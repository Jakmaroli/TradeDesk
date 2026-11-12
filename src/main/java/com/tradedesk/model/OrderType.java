package com.tradedesk.model;

/**
 * Enum: type of stock order.
 * Covers Module 3 — Enums.
 * Owner: Member 2
 */
public enum OrderType {
    MARKET("Market Order"),
    LIMIT("Limit Order");

    private final String displayName;

    OrderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}
