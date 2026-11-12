package com.tradedesk.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks a user's holdings and cash balance.
 * Covers Module 3 — Collections (HashMap), Generics.
 * Owner: Member 2
 */
public class Portfolio {

    private final String username;
    private double cashBalance;
    private final Map<String, Integer> holdings = new HashMap<>();
    private final Map<String, Double> avgBuyPrice = new HashMap<>();

    public Portfolio(String username, double initialCash) {
        this.username    = username;
        this.cashBalance = initialCash;
    }

    public String getUsername() { return username; }

    public synchronized double getCashBalance() { return cashBalance; }

    public synchronized void addCash(double amount) { cashBalance += amount; }

    public synchronized boolean tryDeductCash(double amount) {
        if (amount > cashBalance) return false;
        cashBalance -= amount;
        return true;
    }

    public synchronized void deductCash(double amount) {
        if (amount > cashBalance) throw new IllegalStateException("Insufficient funds");
        cashBalance -= amount;
    }

    public synchronized void addHolding(String ticker, int qty, double price) {
        int existing = holdings.getOrDefault(ticker, 0);
        double existingAvg = avgBuyPrice.getOrDefault(ticker, 0.0);
        double newAvg = (existingAvg * existing + price * qty) / (existing + qty);
        holdings.put(ticker, existing + qty);
        avgBuyPrice.put(ticker, newAvg);
    }

    public synchronized void removeHolding(String ticker, int qty) {
        int existing = holdings.getOrDefault(ticker, 0);
        if (qty > existing) throw new IllegalStateException("Not enough shares to sell");
        int remaining = existing - qty;
        if (remaining == 0) {
            holdings.remove(ticker);
            avgBuyPrice.remove(ticker);
        } else {
            holdings.put(ticker, remaining);
        }
    }

    public synchronized int getHolding(String ticker)     { return holdings.getOrDefault(ticker, 0); }
    public synchronized double getAvgPrice(String ticker) { return avgBuyPrice.getOrDefault(ticker, 0.0); }
    public synchronized Map<String, Integer> getHoldings() { return new HashMap<>(holdings); }

    public synchronized double getTotalValue(Map<String, Double> currentPrices) {
        double marketValue = holdings.entrySet().stream()
                .mapToDouble(e -> e.getValue() * currentPrices.getOrDefault(e.getKey(), 0.0))
                .sum();
        return cashBalance + marketValue;
    }
}
