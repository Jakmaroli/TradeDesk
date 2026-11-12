package com.tradedesk.engine;

import com.tradedesk.model.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Per-ticker order book with buy and sell queues.
 * Uses PriorityQueue for price-time priority.
 * Covers Module 5 — Concurrent data structures;
 *         Module 3 — Generics, Collections.
 * Owner: Member 1 (Jak)
 */
public class OrderBook {

    private final String ticker;

    // Buy side: highest price first (natural Comparable on Order)
    private final PriorityQueue<Order> buyQueue  = new PriorityQueue<>();
    // Sell side: lowest price first
    private final PriorityQueue<Order> sellQueue = new PriorityQueue<>(
            Comparator.comparingDouble(Order::getLimitPrice));

    private final List<Trade> tradeHistory = new ArrayList<>();

    public OrderBook(String ticker) {
        this.ticker = ticker;
    }

    public String getTicker() { return ticker; }

    public synchronized void addBuyOrder(Order order) {
        buyQueue.offer(order);
    }

    public synchronized void addSellOrder(Order order) {
        sellQueue.offer(order);
    }

    /**
     * Try to match the top buy against the top sell.
     * Returns the matched trade, or empty if no match.
     */
    public synchronized Optional<Trade> tryMatch() {
        if (buyQueue.isEmpty() || sellQueue.isEmpty()) return Optional.empty();

        Order buy  = buyQueue.peek();
        Order sell = sellQueue.peek();

        if (buy.getLimitPrice() >= sell.getLimitPrice()) {
            buyQueue.poll();
            sellQueue.poll();

            double execPrice = sell.getLimitPrice(); // sell price used (conservative for buyer)
            buy.execute();
            sell.execute();

            Trade trade = new Trade(buy, sell, execPrice);
            tradeHistory.add(trade);
            return Optional.of(trade);
        }
        return Optional.empty();
    }

    public synchronized int getBuyDepth()  { return buyQueue.size(); }
    public synchronized int getSellDepth() { return sellQueue.size(); }

    public synchronized List<Trade> getTradeHistory() {
        return Collections.unmodifiableList(new ArrayList<>(tradeHistory));
    }

    public synchronized void cancelOrder(Order order) {
        buyQueue.remove(order);
        sellQueue.remove(order);
        order.setStatus(OrderStatus.CANCELLED);
    }
}
