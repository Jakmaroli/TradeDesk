package com.tradedesk.model;

/**
 * Represents a listed stock.
 * Owner: Member 2
 */
public class Stock {

    private final String ticker;
    private final String companyName;
    private final StockSector sector;
    private volatile double currentPrice;
    private double openPrice;

    public Stock(String ticker, String companyName, StockSector sector, double openPrice) {
        this.ticker      = ticker;
        this.companyName = companyName;
        this.sector      = sector;
        this.openPrice   = openPrice;
        this.currentPrice = openPrice;
    }

    public String getTicker()       { return ticker; }
    public String getCompanyName()  { return companyName; }
    public StockSector getSector()  { return sector; }
    public double getCurrentPrice() { return currentPrice; }
    public double getOpenPrice()    { return openPrice; }

    public synchronized void setCurrentPrice(double price) {
        this.currentPrice = price;
    }

    public double getChangePercent() {
        return ((currentPrice - openPrice) / openPrice) * 100.0;
    }

    @Override
    public String toString() {
        return String.format("%s (%s) — ₹%.2f [%+.2f%%]",
                ticker, companyName, currentPrice, getChangePercent());
    }
}
