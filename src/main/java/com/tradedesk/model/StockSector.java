package com.tradedesk.model;

/**
 * Enum: NSE stock sector classification.
 * Owner: Member 2
 */
public enum StockSector {
    IT("Information Technology"),
    BANKING("Banking & Finance"),
    ENERGY("Energy & Oil"),
    PHARMA("Pharmaceuticals"),
    AUTO("Automobile"),
    FMCG("Fast-Moving Consumer Goods"),
    TELECOM("Telecommunications"),
    INFRA("Infrastructure");

    private final String displayName;

    StockSector(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }

    @Override
    public String toString() { return displayName; }
}
