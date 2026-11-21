package com.tradedesk.db;

import com.tradedesk.engine.MatchingEngine;
import com.tradedesk.engine.Trade;
import com.tradedesk.engine.TradeObserver;
import com.tradedesk.model.Portfolio;

/**
 * Persistence side of the Observer pattern (Member 1 — Integration).
 *
 * Registered on the {@link MatchingEngine}; on every settled trade it:
 *   1. logs both sides to {@code trade_history} (no username FK, so bots log fine), and
 *   2. saves the up-to-date portfolio of each *registered* human user.
 *
 * It is deliberately defensive: the market-maker bot is skipped, unknown users
 * are skipped (portfolios/holdings have a FK to users), and any database error
 * is logged but never propagated — a DB hiccup must never stall live trading.
 */
public class PersistenceObserver implements TradeObserver {

    private final MatchingEngine  engine;
    private final TradeHistoryDAO history;
    private final PortfolioDAO    portfolios;
    private final UserDAO         users;
    private final String          botName;   // synthetic account to skip (market maker)

    public PersistenceObserver(MatchingEngine engine, TradeHistoryDAO history,
                               PortfolioDAO portfolios, UserDAO users, String botName) {
        this.engine     = engine;
        this.history    = history;
        this.portfolios = portfolios;
        this.users      = users;
        this.botName    = botName;
    }

    @Override
    public void onTrade(Trade trade) {
        // 1. Log the trade (buyer + seller rows). Safe for bots: no FK on username.
        try {
            history.insertTrade(trade);
        } catch (RuntimeException e) {
            System.err.println("[Persistence] insertTrade failed: " + e.getMessage());
        }

        // 2. Persist each real account's new balance/holdings.
        savePortfolio(trade.getBuyer());
        savePortfolio(trade.getSeller());
    }

    /** Saves one user's portfolio, skipping the bot and anyone not in the users table. */
    private void savePortfolio(String username) {
        if (username == null || username.equals(botName)) return;
        try {
            if (!users.exists(username)) return;           // demo user never registered — nothing to save
            Portfolio pf = engine.getOrCreatePortfolio(username);
            portfolios.save(pf);
        } catch (RuntimeException e) {
            System.err.println("[Persistence] save portfolio failed for " + username + ": " + e.getMessage());
        }
    }

    // Live-update hooks are irrelevant to persistence.
    @Override public void onOrderFilled(com.tradedesk.model.Order order, double executedPrice) { }
    @Override public void onPriceUpdate(String ticker, double newPrice) { }
    @Override public void onOrderRejected(com.tradedesk.model.Order order, String reason) { }
}
