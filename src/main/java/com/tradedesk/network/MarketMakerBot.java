package com.tradedesk.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.tradedesk.engine.MatchingEngine;
import com.tradedesk.engine.OrderBook;
import com.tradedesk.engine.TradeObserver;
import com.tradedesk.model.InsufficientFundsException;
import com.tradedesk.model.InvalidTickerException;
import com.tradedesk.model.LimitOrder;
import com.tradedesk.model.Order;
import com.tradedesk.model.Portfolio;

/**
 * The engine only matches users against each other, so with one user online
 * nothing would ever fill. This bot is the "exchange": on every price tick it
 * offers to SELL slightly above the market price and BUY slightly below it.
 *
 * Covers Design Patterns — Observer; Module 2 — static, final.
 * Owner: Member 3
 */
public class MarketMakerBot implements TradeObserver {

    public static final String BOT_NAME = "MARKET_MAKER";
    public static final double SPREAD   = 0.001; // 0.1 % either side of the market
    private static final int    QUOTE_QTY = 1_000_000;

    private final MatchingEngine engine;
    /** ticker -> {buyQuote, sellQuote} from the last tick, so we can replace them. */
    private final Map<String, Order[]> lastQuotes = new ConcurrentHashMap<>();

    public MarketMakerBot(MatchingEngine engine) {
        this.engine = engine;
        // Give the bot "infinite" cash and shares so it can always trade
        Portfolio pf = engine.getOrCreatePortfolio(BOT_NAME);
        pf.addCash(1e15);
        engine.getStocks().keySet().forEach(t -> pf.addHolding(t, 1_000_000_000, 0.0));
    }

    public static double bidFor(double price) { return round2(price * (1 - SPREAD)); }
    public static double askFor(double price) { return round2(price * (1 + SPREAD)); }
    /** A market buy is allowed to pay up to 0.5 % above the last price. */
    public static double marketBuyLimit(double price) { return round2(price * 1.005); }

    /** Post the first quotes (call once after the engine starts). */
    public void start() {
        engine.getStocks().values().forEach(s -> requote(s.getTicker(), s.getCurrentPrice()));
    }

    @Override
    public void onPriceUpdate(String ticker, double newPrice) {
        requote(ticker, newPrice);
    }

    private void requote(String ticker, double price) {
        OrderBook book = engine.getOrderBooks().get(ticker);
        Order[] old = lastQuotes.get(ticker);
        if (old != null) {               // remove last tick's quotes
            book.cancelOrder(old[0]);
            book.cancelOrder(old[1]);
        }
        Order buy  = new LimitOrder(BOT_NAME, ticker, QUOTE_QTY, bidFor(price));
        Order sell = new LimitOrder(MatchingEngine.SELL_PREFIX + BOT_NAME, ticker, QUOTE_QTY, askFor(price));
        try {
            engine.submitOrder(buy);
            engine.submitOrder(sell);
            lastQuotes.put(ticker, new Order[]{buy, sell});
        } catch (InsufficientFundsException | InvalidTickerException | IllegalArgumentException e) {
            System.err.println("[MarketMaker] " + e.getMessage());
        }
    }

    @Override public void onOrderFilled(Order order, double executedPrice) {}
    @Override public void onOrderRejected(Order order, String reason) {}

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}