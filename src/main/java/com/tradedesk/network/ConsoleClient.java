package com.tradedesk.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Tiny test client: type protocol lines, see server replies.
 * Run:  mvn exec:java -Dexec.mainClass=com.tradedesk.network.ConsoleClient
 * Then type:  HELLO|siri   BUY|TCS|5   PORTFOLIO   QUIT
 * Owner: Member 3 (testing tool)
 */
public class ConsoleClient {
    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : TradeClient.DEFAULT_PORT;

        try (Socket socket = new Socket(host, port)) {
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(socket.getOutputStream(), true);

            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) System.out.println("<< " + line);
                } catch (IOException ignored) {}
                System.out.println("[disconnected]");
                System.exit(0);
            });
            reader.setDaemon(true);
            reader.start();

            System.out.println("Connected to " + host + ":" + port + ". Start with HELLO|yourname");
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
            String line;
            while ((line = keyboard.readLine()) != null) out.println(line);
        }
    }
}