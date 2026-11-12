package com.tradedesk.network;

/**
 * Multi-client TCP server — accepts connections and spawns ClientHandler threads.
 * Owner: Member 3
 * TODO (Member 3): implement full server logic.
 */
public class TradeServer implements Runnable {

    private final int port;

    public TradeServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        // Member 3 implements: ServerSocket accept loop, ClientHandler threads,
        // PriceFeedThread, OrderProcessor, disconnect handling.
        System.out.println("TradeServer starting on port " + port + " — implement me (Member 3)");
    }

    public static void main(String[] args) {
        new Thread(new TradeServer(TradeClient.DEFAULT_PORT), "TradeServer").start();
    }
}
