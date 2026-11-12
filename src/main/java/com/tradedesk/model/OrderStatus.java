package com.tradedesk.model;

/**
 * Enum: lifecycle status of an order.
 * Owner: Member 2
 */
public enum OrderStatus {
    PENDING,
    FILLED,
    PARTIALLY_FILLED,
    CANCELLED,
    REJECTED;

    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED;
    }
}
