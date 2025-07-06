package org.example;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ServerStats maintains runtime statistics and logs for the chat server.
 */
public class ServerStats {
    private static final AtomicInteger activeClients = new AtomicInteger(0);
    private static final List<String> activeRooms = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> recentLogs = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_LOGS = 100;

    public static void clientConnected() {
        int count = activeClients.incrementAndGet();
        addLog("Client connected. Total clients: " + count);
    }

    public static void clientDisconnected() {
        int count = activeClients.decrementAndGet();
        addLog("Client disconnected. Total clients: " + count);
    }

    public static int getActiveClientCount() {
        return activeClients.get();
    }

    public static void setActiveRooms(List<String> rooms) {
        synchronized (activeRooms) {
            activeRooms.clear();
            activeRooms.addAll(rooms);
        }
    }

    public static List<String> getActiveRooms() {
        synchronized (activeRooms) {
            return new ArrayList<>(activeRooms);
        }
    }

    public static void addLog(String log) {
        synchronized (recentLogs) {
            recentLogs.add(log);
            if (recentLogs.size() > MAX_LOGS) {
                recentLogs.remove(0);
            }
        }
    }

    public static List<String> getRecentLogs() {
        synchronized (recentLogs) {
            return new ArrayList<>(recentLogs);
        }
    }
}
