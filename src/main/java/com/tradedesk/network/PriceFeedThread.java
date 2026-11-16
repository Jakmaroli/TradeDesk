package com.tradedesk.network;

import com.tradedesk.engine.MatchingEngine;
import com.tradedesk.engine.TradeObserver;
import com.tradedesk.model.Order;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Observer pattern: the engine calls onPriceUpdate / onOrderFilled / onOrderRejected.
 * We do NOT write to sockets inside those callbacks (a slow client would freeze the
 * engine). Instead we put a message on a queue, and this thread sends it.
 * That is producer-consumer inter-thread communication (BlockingQueue = wait/notify).
 *
 * It also broadcasts the leaderboard every few seconds.
 *
 * Covers Module 5 — Thread, inter-thread communication; Design Patterns — Observer;
 *         Streams & Lambdas (leaderboard).
 * Owner: Member 3
 */
public class PriceFeedThread extends Thread implements TradeObserver {

    private static final long LEADERBOARD_INTERVAL_MS = 5_000;

    /** target == null means "send to everyone". */
    private record Outgoing(String target, String line) {}

    private final BlockingQueue<Outgoing> queue = new LinkedBlockingQueue<>();
    private final ClientRegistry registry;
    private final MatchingEngine engine;
    private volatile boolean running = true;

    public PriceFeedThread(ClientRegistry registry, MatchingEngine engine) {
        super("PriceFeedThread");
        this.registry = registry;
        this.engine   = engine;
        setDaemon(true);
    }

    // ── TradeObserver callbacks (run on ENGINE threads — keep them fast) ────
    @Override
    public void onPriceUpdate(String ticker, double newPrice) {
        queue.offer(new Outgoing(null, "PRICE|" + ticker + "|" + ClientHandler.money(newPrice)));
    }

    @Override
    public void onOrderFilled(Order order, double executedPrice) {
        String user = MatchingEngine.ownerOf(order);
        if (user.equals(MarketMakerBot.BOT_NAME)) return;
        queue.offer(new Outgoing(user, "FILL|" + order.getOrderId() + "|" + order.getTicker() + "|"
                + order.getQuantity() + "|" + ClientHandler.money(executedPrice)));
    }

    @Override
    public void onOrderRejected(Order order, String reason) {
        String user = MatchingEngine.ownerOf(order);
        if (user.equals(MarketMakerBot.BOT_NAME)) return;
        queue.offer(new Outgoing(user, "REJECT|" + order.getOrderId() + "|" + reason));
    }

    // ── Sender loop ────────────────────────────────────────────────────────
    @Override
    public void run() {
        long nextLeaderboard = System.currentTimeMillis() + LEADERBOARD_INTERVAL_MS;
        while (running) {
            try {
                // Waits (blocks) until a message arrives, or 500 ms pass
                Outgoing msg = queue.poll(500, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    if (msg.target() == null) registry.broadcast(msg.line());
                    else                      registry.sendTo(msg.target(), msg.line());
                }
                if (System.currentTimeMillis() >= nextLeaderboard) {
                    broadcastLeaderboard();
                    nextLeaderboard = System.currentTimeMillis() + LEADERBOARD_INTERVAL_MS;
                }
            } catch (InterruptedException e) {
                running = false;
            } catch (RuntimeException e) {
                System.err.println("[PriceFeed] " + e.getMessage()); // keep the feed alive
            }
        }
    }

    private void broadcastLeaderboard() {
        List<Map.Entry<String, Double>> board = engine.getLeaderboard();
        String body = board.stream()
                .filter(e -> !e.getKey().equals(MarketMakerBot.BOT_NAME))
                .limit(10)
                .map(e -> e.getKey() + ":" + ClientHandler.money(e.getValue()))
                .collect(Collectors.joining(","));
        if (!body.isEmpty()) registry.broadcast("LEADERBOARD|" + body);
    }

    public void shutdown() {
        running = false;
        interrupt();
    }
}