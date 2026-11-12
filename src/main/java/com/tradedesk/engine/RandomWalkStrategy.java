package com.tradedesk.engine;

import com.tradedesk.model.Stock;

import java.util.Random;

/**
 * Random-walk (Brownian motion) pricing strategy.
 * Price drifts ±2 % each tick with a small positive drift bias.
 * Covers Design Patterns — Strategy (concrete strategy).
 * Owner: Member 1 (Jak)
 */
public class RandomWalkStrategy implements PricingStrategy {

    private static final double VOLATILITY  = 0.02;  // 2 % std-dev
    private static final double DRIFT       = 0.0005; // slight upward bias
    private final Random rng;

    public RandomWalkStrategy() {
        this.rng = new Random();
    }

    public RandomWalkStrategy(long seed) {
        this.rng = new Random(seed); // deterministic for tests
    }

    @Override
    public double nextPrice(Stock stock) {
        double current   = stock.getCurrentPrice();
        double shock     = rng.nextGaussian() * VOLATILITY;
        double newPrice  = current * Math.exp(DRIFT + shock);
        // Floor at 1 rupee
        return Math.max(1.0, Math.round(newPrice * 100.0) / 100.0);
    }
}
