package com.tradedesk.network;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.tradedesk.engine.MatchingEngine;
import com.tradedesk.engine.RandomWalkStrategy;
import com.tradedesk.db.ConnectionPool;
import com.tradedesk.db.PersistenceObserver;
import com.tradedesk.db.PortfolioDAO;
import com.tradedesk.db.TradeHistoryDAO;
import com.tradedesk.db.UserDAO;

/**
 * Multi-client TCP server.
 *  - starts the MatchingEngine on its own thread
 *  - starts PriceFeedThread (broadcasts prices / fills / leaderboard)
 *  - accepts sockets and spawns one ClientHandler thread per client
 *  - on shutdown, closes everything and uses join() / isAlive() to confirm threads stopped
 *
 * Run:  mvn compile exec:java -Dexec.mainClass=com.tradedesk.network.TradeServer
 *
 * Covers Module 5 — Thread, Runnable, join(), isAlive(); Sockets (ServerSocket).
 * Owner: Member 3
 */
public class TradeServer implements Runnable {

    public static final int MAX_CLIENTS = 50;

    private final int            port;
    private final MatchingEngine engine;
    private final ClientRegistry registry = new ClientRegistry();
    private final List<Thread>   handlerThreads = new CopyOnWriteArrayList<>();

    private Thread          engineThread;
    private PriceFeedThread priceFeed;
    private ServerSocket    serverSocket;
    private volatile boolean running = false;
    private int clientCounter = 0;

    public TradeServer(int port) {
        this.port   = port;
        this.engine = new MatchingEngine(new RandomWalkStrategy());
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.err.println("[Server] Cannot open port " + port + ": " + e.getMessage());
            return;
        }
        running = true;

        // 1. Engine thread (matching + price simulation)
        engineThread = new Thread(engine, "MatchingEngine");
        engineThread.start();

        // 2. Broadcaster thread, registered as an engine observer
        priceFeed = new PriceFeedThread(registry, engine);
        engine.addObserver(priceFeed);
        priceFeed.start();

        // 3. Market maker so orders have someone to trade with
        MarketMakerBot bot = new MarketMakerBot(engine);
        engine.addObserver(bot);
        bot.start();

        // 4. Persistence: save every settled trade to MySQL, if the DB is reachable.
        //    Guarded so the server still runs (in-memory only) when MySQL is down.
        try {
            ConnectionPool.getInstance(); // opens the pool; throws if MySQL is unreachable
            engine.addObserver(new PersistenceObserver(
                    engine, new TradeHistoryDAO(), new PortfolioDAO(), new UserDAO(), MarketMakerBot.BOT_NAME));
            System.out.println("[Server] Persistence ON — trades save to MySQL.");
        } catch (Exception e) {
            System.out.println("[Server] Persistence OFF — DB unreachable (" + e.getMessage()
                    + "). Trading works but nothing is saved.");
        }

        System.out.println("[Server] TradeDesk server listening on port " + port);

        // 4. Accept loop
        while (running) {
            try {
                Socket socket = serverSocket.accept();   // blocks until a client connects
                handlerThreads.removeIf(t -> !t.isAlive()); // forget finished threads

                if (registry.size() >= MAX_CLIENTS) {
                    new PrintWriter(socket.getOutputStream(), true).println("ERROR|Server full");
                    socket.close();
                    continue;
                }

                socket.setTcpNoDelay(true);
                ClientHandler handler = new ClientHandler(socket, engine, registry);
                registry.add(handler);

                Thread t = new Thread(handler, "Client-" + (++clientCounter));
                handlerThreads.add(t);
                t.start();
                System.out.println("[Server] Connection from " + socket.getRemoteSocketAddress()
                        + " -> " + t.getName());
            } catch (SocketException e) {
                if (running) System.err.println("[Server] Socket error: " + e.getMessage());
                // when shutdown() closes the ServerSocket, accept() throws -> loop ends
            } catch (IOException e) {
                System.err.println("[Server] Accept failed: " + e.getMessage());
            }
        }
    }

    /** Stop everything cleanly and prove the threads are dead with join() + isAlive(). */
    public void shutdown() {
        if (!running) return;
        running = false;
        System.out.println("[Server] Shutting down...");

        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        registry.closeAll("Server shutting down");

        try {
            for (Thread t : handlerThreads) {
                t.join(2_000);
                System.out.println("[Server]   " + t.getName() + " alive? " + t.isAlive());
            }
            engine.stop();
            engineThread.join(2_000);
            System.out.println("[Server]   MatchingEngine alive? " + engineThread.isAlive());
            priceFeed.shutdown();
            priceFeed.join(2_000);
            System.out.println("[Server]   PriceFeedThread alive? " + priceFeed.isAlive());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[Server] Bye.");
    }

    public MatchingEngine getEngine() { return engine; }

    public static void main(String[] args) throws InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : TradeClient.DEFAULT_PORT;
        TradeServer server = new TradeServer(port);

        // Ctrl+C -> clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "ShutdownHook"));

        Thread serverThread = new Thread(server, "TradeServer");
        serverThread.start();
        serverThread.join(); // main thread waits here until the server stops
    }
}