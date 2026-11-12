package com.tradedesk.model;

/**
 * Thrown when a user tries to buy more than their cash balance allows.
 * Covers Module 4 — Custom Exceptions.
 * Owner: Member 2
 */
public class InsufficientFundsException extends Exception {

    private final double required;
    private final double available;

    public InsufficientFundsException(double required, double available) {
        super(String.format("Insufficient funds: need ₹%.2f but only ₹%.2f available",
                required, available));
        this.required  = required;
        this.available = available;
    }

    public double getRequired()  { return required; }
    public double getAvailable() { return available; }
    public double getShortfall() { return required - available; }
}
