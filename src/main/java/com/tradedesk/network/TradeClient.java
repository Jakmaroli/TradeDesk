package com.tradedesk.network;

import com.tradedesk.engine.TradeObserver;
import com.tradedesk.model.Order;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Socket client that connects to TradeServer (Member 3).
 * Sends BUY/SELL commands, receives PRICE and FILL events.
 *
 * Protocol (plain-text, newline-delimited):
 *   Client → Server: BUY|RELIANCE|10
 *                    SELL|TCS|5|4100.00
 *                    CANCEL|<orderId>
 *   Server → Client: PRICE|INFOSYS|1432.50
 *                    FILL|<orderId>|RELIANCE|10|2860.00
 *                    REJECT|<orderId>|<reason>
 *
 * Covers Module 5 — Sockets, threads.
 * Owner: Member 1 (Jak)
 */
public class TradeClient implements Runnable {

    public static final int DEFAULT_PORT = 9876;

    private final String host;
    private final int    port;
    private final String username;
    private final List<TradeObserver> observers = new CopyOnWriteArrayList<>();

    private Socket         socket;
    private PrintWriter    out;
    private BufferedReader in;
    private volatile boolean connected = false;

    public TradeClient(String host, int port, String username) {
        this.host     = host;
        this.port     = port;
        this.username = username;
    }

    // ── Connection ─────────────────────────────────────────────────────────
    public void connect() throws IOException {
        socket = new Socket(host, port);
        out    = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        connected = true;
        // Identify ourselves to the server
        send("HELLO|" + username);
    }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public boolean isConnected() { return connected; }

    // ── Send Commands ──────────────────────────────────────────────────────
    public void sendMarketBuy(String ticker, int qty) {
        send("BUY|" + ticker + "|" + qty);
    }

    public void sendLimitBuy(String ticker, int qty, double limit) {
        send("BUY|" + ticker + "|" + qty + "|" + limit);
    }

    public void sendMarketSell(String ticker, int qty) {
        send("SELL|" + ticker + "|" + qty);
    }

    public void sendLimitSell(String ticker, int qty, double limit) {
        send("SELL|" + ticker + "|" + qty + "|" + limit);
    }

    public void sendCancel(String orderId) {
        send("CANCEL|" + orderId);
    }

    private void send(String message) {
        if (out != null && connected) out.println(message);
    }

    // ── Observer Registry ─────────────────────────────────────────────────
    public void addObserver(TradeObserver obs)    { observers.add(obs); }
    public void removeObserver(TradeObserver obs) { observers.remove(obs); }

    // ── Receive Loop ───────────────────────────────────────────────────────
    @Override
    public void run() {
        try {
            String line;
            while (connected && (line = in.readLine()) != null) {
                handleMessage(line.trim());
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("[TradeClient] Connection lost: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private static final int MAX_LINE_LENGTH = 8192;

    private void handleMessage(String msg) {
        if (msg.length() > MAX_LINE_LENGTH) {
            System.err.println("[TradeClient] Dropped oversized message (" + msg.length() + " chars)");
            return;
        }

        String[] parts = msg.split("\\|", 6);
        if (parts.length < 2) return;

        try {
            switch (parts[0]) {
                case "PRICE" -> {
                    if (parts.length >= 3) {
                        String ticker = parts[1];
                        double price  = Double.parseDouble(parts[2]);
                        if (price < 0 || !Double.isFinite(price)) return;
                        observers.forEach(o -> o.onPriceUpdate(ticker, price));
                    }
                }
                case "FILL" -> {
                    System.out.println("[FILL] " + msg);
                }
                case "REJECT" -> {
                    System.err.println("[REJECT] " + msg);
                }
                case "LEADERBOARD" -> {
                    System.out.println("[LEADERBOARD] " + parts[1]);
                }
                default -> System.out.println("[SERVER] " + msg);
            }
        } catch (NumberFormatException e) {
            System.err.println("[TradeClient] Malformed numeric in message: " + e.getMessage());
        }
    }
}
