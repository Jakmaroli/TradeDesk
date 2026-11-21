package com.tradedesk.engine;

import com.tradedesk.model.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Core trading engine:
 *  - Accepts incoming orders via a BlockingQueue (wait/notify replaced by take())
 *  - Maintains per-ticker OrderBooks
 *  - Runs price simulation via PricingStrategy
 *  - Notifies registered TradeObservers (Observer pattern)
 *  - Computes P&L leaderboard using Streams
 *
 * Covers Module 5 — Multithreading (Runnable, Thread, BlockingQueue, ConcurrentHashMap);
 *         Module 3 — Collections, Generics;
 *         Design Patterns — Observer, Strategy, MVC (this is the Model part).
 * Owner: Member 1 (Jak)
 */
public class MatchingEngine implements Runnable {

    // ── State ──────────────────────────────────────────────────────────────
    private final Map<String, Stock>     stocks    = new ConcurrentHashMap<>();
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, Portfolio> portfolios = new ConcurrentHashMap<>();

    /** Incoming orders land here; engine thread blocks on take(). */
    private final BlockingQueue<Order> incomingOrders = new LinkedBlockingQueue<>();

    /** Registered UI / network observers. */
    private final List<TradeObserver>   observers  = new CopyOnWriteArrayList<>();

    private final PricingStrategy pricingStrategy;
    private volatile boolean running = false;

    // Price tick interval in ms
    private static final long TICK_INTERVAL_MS = 2_000L;

    /** Marker on the username that tells the engine an order is a SELL. */
    public static final String SELL_PREFIX = "SELL:";

    // ── Constructor ────────────────────────────────────────────────────────
    public MatchingEngine(PricingStrategy strategy) {
        this.pricingStrategy = strategy;
        initStocks();
    }

    // ── Stock Initialisation ───────────────────────────────────────────────
    private void initStocks() {
        addStock(new Stock("RELIANCE",  "Reliance Industries",   StockSector.ENERGY,    2850.00));
        addStock(new Stock("TCS",       "Tata Consultancy Svcs", StockSector.IT,        4120.00));
        addStock(new Stock("INFOSYS",   "Infosys Ltd",           StockSector.IT,        1430.00));
        addStock(new Stock("HDFCBANK",  "HDFC Bank",             StockSector.BANKING,   1680.00));
        addStock(new Stock("WIPRO",     "Wipro Ltd",             StockSector.IT,         560.00));
        addStock(new Stock("SUNPHARMA", "Sun Pharmaceutical",    StockSector.PHARMA,    1210.00));
        addStock(new Stock("MARUTI",    "Maruti Suzuki",         StockSector.AUTO,     12400.00));
        addStock(new Stock("AIRTEL",    "Bharti Airtel",         StockSector.TELECOM,   1350.00));
    }

    private void addStock(Stock stock) {
        stocks.put(stock.getTicker(), stock);
        orderBooks.put(stock.getTicker(), new OrderBook(stock.getTicker()));
    }

    // ── Observer Registry ─────────────────────────────────────────────────
    public void addObserver(TradeObserver observer)    { observers.add(observer); }
    public void removeObserver(TradeObserver observer) { observers.remove(observer); }

    // ── Portfolio Management ───────────────────────────────────────────────
    public Portfolio getOrCreatePortfolio(String username) {
        return portfolios.computeIfAbsent(username, u -> new Portfolio(u, 1_000_000.0));
    }

    /** Seed a user's portfolio from a saved state (e.g. loaded from the DB at login). */
    public void loadPortfolio(Portfolio p) {
        if (p != null) portfolios.put(p.getUsername(), p);
    }

    // ── Public API ─────────────────────────────────────────────────────────
    /**
     * Submit an order from a client (or the UI).
     * Thread-safe: just puts onto the queue.
     */
    public void submitOrder(Order order) throws InsufficientFundsException, InvalidTickerException {
        // Validate ticker
        Stock stock = stocks.get(order.getTicker());
        if (stock == null) {
            throw new InvalidTickerException(order.getTicker());
        }

        Portfolio pf = getOrCreatePortfolio(ownerOf(order));

        if (isBuyOrder(order)) {
            // Buy: market orders cost the current price, limit orders cost the limit price
            double unitPrice = (order instanceof MarketOrder)
                    ? stock.getCurrentPrice()
                    : order.getLimitPrice();
            double cost = unitPrice * order.getQuantity();
            if (cost > pf.getCashBalance()) {
                throw new InsufficientFundsException(cost, pf.getCashBalance());
            }
        } else {
            // Sell: the user must actually own the shares (no cash check for sells)
            int owned = pf.getHolding(order.getTicker());
            if (owned < order.getQuantity()) {
                throw new IllegalArgumentException("Not enough shares: you own " + owned
                        + " " + order.getTicker() + " but tried to sell " + order.getQuantity());
            }
        }

        incomingOrders.offer(order);
    }

    /** Sell orders carry a "SELL:" prefix on the username; this returns the real user. */
    public static String ownerOf(Order order) {
        String u = order.getUsername();
        return u.startsWith(SELL_PREFIX) ? u.substring(SELL_PREFIX.length()) : u;
    }

    // Market order convenience
    public void submitMarketBuy(String username, String ticker, int qty)
            throws InsufficientFundsException, InvalidTickerException {
        Stock stock  = stocks.get(ticker);
        if (stock == null) throw new InvalidTickerException(ticker);
        MarketOrder order = new MarketOrder(username, ticker, qty);
        order.setExecutionPrice(stock.getCurrentPrice());
        submitOrder(order);
    }

    public void submitLimitBuy(String username, String ticker, int qty, double limitPrice)
            throws InsufficientFundsException, InvalidTickerException {
        submitOrder(new LimitOrder(username, ticker, qty, limitPrice));
    }

    // ── Leaderboard (Streams) ──────────────────────────────────────────────
    /**
     * Returns a sorted leaderboard: username → total portfolio value.
     * Uses Java Streams throughout.
     * Covers Module 3 — Streams, Lambdas.
     */
    public List<Map.Entry<String, Double>> getLeaderboard() {
        Map<String, Double> currentPrices = stocks.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().getCurrentPrice()));

        return portfolios.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().getTotalValue(currentPrices)))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
    }

    /**
     * P&L for a user: current value minus starting capital (₹10 lakh).
     */
    public double getPnL(String username) {
        Portfolio pf = portfolios.get(username);
        if (pf == null) return 0.0;
        Map<String, Double> prices = stocks.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().getCurrentPrice()));
        return pf.getTotalValue(prices) - 1_000_000.0;
    }

    // ── Getters ────────────────────────────────────────────────────────────
    public Map<String, Stock>     getStocks()     { return Collections.unmodifiableMap(stocks); }
    public Stock                  getStock(String ticker) { return stocks.get(ticker); }
    public Map<String, OrderBook> getOrderBooks() { return Collections.unmodifiableMap(orderBooks); }

    // ── Engine Thread ──────────────────────────────────────────────────────
    @Override
    public void run() {
        running = true;
        Thread priceTicker = new Thread(this::priceFeedLoop, "PriceFeed");
        priceTicker.setDaemon(true);
        priceTicker.start();

        while (running) {
            try {
                // Blocks until an order arrives — demonstrates wait/notify semantics via BlockingQueue
                Order order = incomingOrders.take();
                processOrder(order);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (RuntimeException e) {
                // One bad order must never kill the engine thread
                System.err.println("[Engine] Error processing order: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        // Poison pill to unblock take()
        incomingOrders.offer(new MarketOrder("__STOP__", "RELIANCE", 0));
    }

    // ── Internal Processing ────────────────────────────────────────────────
    private void processOrder(Order order) {
        if ("__STOP__".equals(order.getUsername())) return;

        String ticker    = order.getTicker();
        OrderBook book   = orderBooks.get(ticker);
        if (book == null) {
            notifyRejected(order, "Unknown ticker");
            return;
        }

        // Route to the right side of the book
        boolean isBuy = isBuyOrder(order);
        if (isBuy) {
            // Market order: set current price now
            if (order instanceof MarketOrder mo) {
                mo.setExecutionPrice(stocks.get(ticker).getCurrentPrice());
            }
            book.addBuyOrder(order);
        } else {
            book.addSellOrder(order);
        }

        // Try to match (keep matching while the book is crossed)
        matchAll(book);
    }

    /** Match, settle and notify every crossing order in the book. */
    private void matchAll(OrderBook book) {
        Optional<Trade> trade;
        while ((trade = book.tryMatch()).isPresent()) {
            Trade t = trade.get();
            try {
                settleTrade(t);
                notifyFilled(t.getBuyOrder(),  t.getExecutedPrice());
                notifyFilled(t.getSellOrder(), t.getExecutedPrice());
                notifyTrade(t); // persistence hook: only fired after a clean settle
            } catch (IllegalStateException e) {
                // e.g. buyer spent the cash elsewhere before this matched
                t.getBuyOrder().setStatus(OrderStatus.CANCELLED);
                t.getSellOrder().setStatus(OrderStatus.CANCELLED);
                notifyRejected(t.getBuyOrder(),  e.getMessage());
                notifyRejected(t.getSellOrder(), e.getMessage());
            }
        }
    }

    private boolean isBuyOrder(Order order) {
        // Convention: buy orders arrive from BUY network command.
        // We distinguish via a simple marker stored in the username prefix added by ClientHandler.
        // For the standalone engine we treat all submitted orders as buys unless marked SELL:.
        return !order.getUsername().startsWith(SELL_PREFIX);
    }

    private void settleTrade(Trade trade) {
        Portfolio buyer  = getOrCreatePortfolio(ownerOf(trade.getBuyOrder()));
        Portfolio seller = getOrCreatePortfolio(ownerOf(trade.getSellOrder()));
        double cost = trade.getExecutedPrice() * trade.getQuantity();

        // Check both sides BEFORE changing anything, so a failure leaves no half-trade
        if (buyer.getCashBalance() < cost) {
            throw new IllegalStateException("Insufficient funds at execution");
        }
        if (seller.getHolding(trade.getTicker()) < trade.getQuantity()) {
            throw new IllegalStateException("Seller no longer has the shares");
        }

        buyer.deductCash(cost);
        buyer.addHolding(trade.getTicker(), trade.getQuantity(), trade.getExecutedPrice());

        seller.removeHolding(trade.getTicker(), trade.getQuantity());
        seller.addCash(cost);
    }

    // ── Price Feed ─────────────────────────────────────────────────────────
    private void priceFeedLoop() {
        while (running) {
            try {
                Thread.sleep(TICK_INTERVAL_MS);
                stocks.values().forEach(stock -> {
                    double newPrice = pricingStrategy.nextPrice(stock);
                    stock.setCurrentPrice(newPrice);
                    notifyPriceUpdate(stock.getTicker(), newPrice);
                    tryFillLimitOrders(stock);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Scan limit orders that become fillable after a price move. */
    private void tryFillLimitOrders(Stock stock) {
        OrderBook book = orderBooks.get(stock.getTicker());
        if (book == null) return;
        matchAll(book); // re-attempt on price change, and settle what matches
    }

    // ── Observer Notifications ─────────────────────────────────────────────
    private void notifyFilled(Order order, double price) {
        observers.forEach(o -> o.onOrderFilled(order, price));
    }
    private void notifyPriceUpdate(String ticker, double price) {
        observers.forEach(o -> o.onPriceUpdate(ticker, price));
    }
    private void notifyRejected(Order order, String reason) {
        observers.forEach(o -> o.onOrderRejected(order, reason));
    }
    private void notifyTrade(Trade trade) {
        observers.forEach(o -> o.onTrade(trade));
    }
}