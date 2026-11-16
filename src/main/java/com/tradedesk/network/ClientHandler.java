package com.tradedesk.network;

import com.tradedesk.engine.MatchingEngine;
import com.tradedesk.model.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One thread per connected user.
 * Reads lines from the socket, parses the protocol, hands orders to the engine,
 * and writes replies back.
 *
 * Client -> Server:  HELLO|user   BUY|T|Q   BUY|T|Q|LIMIT   SELL|T|Q   SELL|T|Q|LIMIT
 *                    CANCEL|ORDER_ID   PORTFOLIO   QUIT
 * Server -> Client:  WELCOME|user|cash   ACK|id|SIDE|T|Q|PRICE   CANCELLED|id
 *                    PORTFOLIO|cash|T:qty,...   ERROR|reason
 *                    (PRICE / FILL / REJECT / LEADERBOARD come from PriceFeedThread)
 *
 * Covers Module 5 — Runnable, synchronized; Module 4 — try-catch, custom exceptions;
 *         Module 1 — string parsing, loops.
 * Owner: Member 3
 */
public class ClientHandler implements Runnable {

    public static final int MAX_ORDER_SIZE   = 10_000;
    private static final int HELLO_TIMEOUT_MS = 30_000;
    private static final int MAX_LINE_LENGTH  = 1_000;

    private final Socket         socket;
    private final MatchingEngine engine;
    private final ClientRegistry registry;

    private PrintWriter    out;
    private BufferedReader in;
    private volatile String  username;      // null until HELLO
    private volatile boolean open = true;

    /** Orders this user placed, so CANCEL|id can find them. */
    private final Map<String, Order> myOrders = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket, MatchingEngine engine, ClientRegistry registry) {
        this.socket   = socket;
        this.engine   = engine;
        this.registry = registry;
    }

    public String  getUsername() { return username; }
    public boolean isLoggedIn()  { return username != null && open; }

    // ── Thread body ────────────────────────────────────────────────────────
    @Override
    public void run() {
        String who = socket.getRemoteSocketAddress().toString();
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

            // 1. First message MUST be HELLO (give the client 30 s to send it)
            socket.setSoTimeout(HELLO_TIMEOUT_MS);
            if (!handleHello(in.readLine())) return;
            socket.setSoTimeout(0); // no timeout after login

            // 2. Main loop: one command per line
            String line;
            while (open && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.length() > MAX_LINE_LENGTH) { send("ERROR|Line too long"); continue; }
                if (!handleCommand(line)) break; // QUIT
            }
        } catch (SocketTimeoutException e) {
            send("ERROR|No HELLO received");
        } catch (IOException e) {
            if (open) System.out.println("[Server] Connection lost: " + who + " (" + e.getMessage() + ")");
        } finally {
            close(null);
            System.out.println("[Server] Disconnected: " + (username != null ? username : who)
                    + " | online now: " + registry.size());
        }
    }

    // ── HELLO ──────────────────────────────────────────────────────────────
    private boolean handleHello(String line) {
        if (line == null) return false;
        String[] p = line.trim().split("\\|");
        if (p.length != 2 || !p[0].equals("HELLO")) {
            send("ERROR|First message must be HELLO|USERNAME");
            return false;
        }
        String name = p[1].trim();
        if (!name.matches("[A-Za-z0-9_]{3,20}") || name.equals(MarketMakerBot.BOT_NAME)) {
            send("ERROR|Username must be 3-20 letters, digits or _");
            return false;
        }

        username = name;
        ClientHandler old = registry.login(name, this);
        if (old != null && old != this) {
            // Reconnect: same user logged in again -> kick the old connection.
            // The portfolio lives in the engine, so nothing is lost.
            old.close("Logged in from another connection");
            System.out.println("[Server] Reconnect: " + name);
        } else {
            System.out.println("[Server] Logged in: " + name + " | online now: " + registry.size());
        }

        Portfolio pf = engine.getOrCreatePortfolio(name);
        send("WELCOME|" + name + "|" + money(pf.getCashBalance()));
        // Send current prices right away, so the client doesn't wait for the next tick
        engine.getStocks().values().forEach(s ->
                send("PRICE|" + s.getTicker() + "|" + money(s.getCurrentPrice())));
        return true;
    }

    // ── Commands ───────────────────────────────────────────────────────────
    /** @return false when the client wants to quit */
    private boolean handleCommand(String line) {
        String[] p = line.split("\\|");
        String cmd = p[0].toUpperCase(Locale.ROOT);

        switch (cmd) {
            case "BUY", "SELL" -> handleOrder(cmd, p);
            case "CANCEL"      -> handleCancel(p);
            case "PORTFOLIO"   -> sendPortfolio();
            case "QUIT"        -> { send("BYE"); return false; }
            case "HELLO"       -> send("ERROR|Already logged in");
            default            -> send("ERROR|Unknown command: " + cmd);
        }
        return true;
    }

    private void handleOrder(String side, String[] p) {
        // Format: BUY|TICKER|QTY   or   BUY|TICKER|QTY|LIMIT_PRICE
        if (p.length != 3 && p.length != 4) {
            send("REJECT|-|Format is " + side + "|TICKER|QTY[|LIMIT_PRICE]");
            return;
        }

        String ticker = p[1].trim().toUpperCase(Locale.ROOT);
        int qty;
        double limit = 0;
        try {
            qty = Integer.parseInt(p[2].trim());
            if (p.length == 4) limit = Double.parseDouble(p[3].trim());
        } catch (NumberFormatException e) {
            send("REJECT|-|Quantity and price must be numbers");
            return;
        }
        if (qty <= 0 || qty > MAX_ORDER_SIZE) {
            send("REJECT|-|Quantity must be between 1 and " + MAX_ORDER_SIZE);
            return;
        }
        if (p.length == 4 && (limit <= 0 || !Double.isFinite(limit))) {
            send("REJECT|-|Limit price must be positive");
            return;
        }

        Stock stock = engine.getStock(ticker);
        if (stock == null) {
            send("REJECT|-|Unknown ticker " + ticker);
            return;
        }

        // A market order is sent to the engine as a limit order that is sure to
        // cross the market maker's quote (buy: a bit above market, sell: at the bid).
        boolean isMarket = (p.length == 3);
        double price = isMarket
                ? (side.equals("BUY") ? MarketMakerBot.marketBuyLimit(stock.getCurrentPrice())
                                      : MarketMakerBot.bidFor(stock.getCurrentPrice()))
                : limit;

        // The engine recognises sells by the "SELL:" prefix on the username
        String owner = side.equals("SELL") ? MatchingEngine.SELL_PREFIX + username : username;
        Order order = new LimitOrder(owner, ticker, qty, price);

        try {
            engine.submitOrder(order);
            myOrders.put(order.getOrderId(), order);
            send("ACK|" + order.getOrderId() + "|" + side + "|" + ticker + "|" + qty + "|"
                    + (isMarket ? "MARKET" : money(limit)));
        } catch (InsufficientFundsException | InvalidTickerException e) {
            send("REJECT|" + order.getOrderId() + "|" + e.getMessage());
        } catch (IllegalArgumentException e) {   // e.g. not enough shares to sell
            send("REJECT|" + order.getOrderId() + "|" + e.getMessage());
        }
    }

    private void handleCancel(String[] p) {
        if (p.length != 2) { send("REJECT|-|Format is CANCEL|ORDER_ID"); return; }
        String id = p[1].trim();
        try {
            Order order = myOrders.get(id);
            if (order == null) throw new OrderNotFoundException(id);
            if (order.getStatus() != OrderStatus.PENDING) {
                send("REJECT|" + id + "|Order is already " + order.getStatus());
                return;
            }
            engine.getOrderBooks().get(order.getTicker()).cancelOrder(order);
            myOrders.remove(id);
            send("CANCELLED|" + id);
        } catch (OrderNotFoundException e) {
            send("REJECT|" + id + "|" + e.getMessage());
        }
    }

    private void sendPortfolio() {
        Portfolio pf = engine.getOrCreatePortfolio(username);
        StringBuilder sb = new StringBuilder();
        pf.getHoldings().forEach((t, q) -> {
            if (sb.length() > 0) sb.append(',');
            sb.append(t).append(':').append(q);
        });
        send("PORTFOLIO|" + money(pf.getCashBalance()) + "|" + sb);
    }

    // ── Output ─────────────────────────────────────────────────────────────
    /**
     * Many threads call this (this handler, PriceFeedThread...), so it is synchronized:
     * two messages can never get mixed up on the same socket.
     */
    public synchronized void send(String line) {
        if (out == null || !open) return;
        out.println(line);
        if (out.checkError()) close(null); // client is gone
    }

    /** Close the connection. Safe to call more than once and from any thread. */
    public void close(String reason) {
        synchronized (this) {
            if (!open) return;
            if (reason != null && out != null) out.println("ERROR|" + reason);
            open = false;
        }
        registry.remove(this);
        try { socket.close(); } catch (IOException ignored) {}
    }

    static String money(double v) {
        return String.format(Locale.US, "%.2f", v);
    }
}