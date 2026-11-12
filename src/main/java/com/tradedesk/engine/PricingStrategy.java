package com.tradedesk.engine;

import com.tradedesk.model.Stock;

/**
 * Strategy interface for price simulation algorithms.
 * Covers Design Patterns — Strategy.
 * Owner: Member 1 (Jak)
 */
public interface PricingStrategy {
    /**
     * Calculate the next price for a stock.
     * @param stock  the stock to price
     * @return       the new price
     */
    double nextPrice(Stock stock);
}
