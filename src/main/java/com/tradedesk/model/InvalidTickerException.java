package com.tradedesk.model;

/**
 * Thrown when a stock ticker symbol is not recognised.
 * Covers Module 4 — Custom Exceptions.
 * Owner: Member 2
 */
public class InvalidTickerException extends Exception {

    private final String ticker;

    public InvalidTickerException(String ticker) {
        super("Unknown ticker symbol: " + ticker);
        this.ticker = ticker;
    }

    public String getTicker() { return ticker; }
}
