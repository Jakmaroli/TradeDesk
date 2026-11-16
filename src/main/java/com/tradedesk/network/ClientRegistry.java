package com.tradedesk.network;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps track of every connected client.
 *  - a list of all handlers (for broadcasting prices to everyone)
 *  - a map username -> handler (for sending FILL / REJECT to one user)
 *
 * Both collections are thread-safe because many threads use them at once:
 * the accept loop adds, ClientHandler threads remove, PriceFeedThread reads.
 *
 * Covers Module 5 — thread-safe collections; Generics.
 * Owner: Member 3
 */
public class ClientRegistry {

    private final List<ClientHandler>        allClients = new CopyOnWriteArrayList<>();
    private final Map<String, ClientHandler> byUsername = new ConcurrentHashMap<>();

    /** Called when a socket connects (before HELLO). */
    public void add(ClientHandler handler) {
        allClients.add(handler);
    }

    /**
     * Called after a successful HELLO.
     * @return the OLD handler if this user was already connected (reconnect), else null
     */
    public ClientHandler login(String username, ClientHandler handler) {
        return byUsername.put(username, handler);
    }

    /** Called when a client disconnects. Only removes the user entry if it is still ours. */
    public void remove(ClientHandler handler) {
        allClients.remove(handler);
        if (handler.getUsername() != null) {
            byUsername.remove(handler.getUsername(), handler);
        }
    }

    /** Send a line to every logged-in client. */
    public void broadcast(String line) {
        for (ClientHandler c : allClients) {
            if (c.isLoggedIn()) c.send(line);
        }
    }

    /** Send a line to one user (if online). */
    public void sendTo(String username, String line) {
        ClientHandler c = byUsername.get(username);
        if (c != null) c.send(line);
    }

    public int size() { return allClients.size(); }

    /** Close every connection — used on server shutdown. */
    public void closeAll(String reason) {
        for (ClientHandler c : allClients) c.close(reason);
    }
}