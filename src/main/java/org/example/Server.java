package org.example;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import java.io.PrintWriter;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;

/**
 * ChatServer listens for client connections and schedules backups + graceful shutdown.
 */
public class Server {
    private static final int PORT = 9999;

    private static ServerSocket serverSocket;
    private static ExecutorService executor;
    private static ScheduledExecutorService scheduler;

    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<PrintWriter>> roomClients
        = new ConcurrentHashMap<>();

    public static void registerClient(String room, PrintWriter out) {
        roomClients
            .computeIfAbsent(room, r -> new CopyOnWriteArrayList<>())
            .add(out);
        ServerStats.setActiveRooms(new ArrayList<>(roomClients.keySet()));
    }

    public static void unregisterClient(String room, PrintWriter out) {
        CopyOnWriteArrayList<PrintWriter> list = roomClients.get(room);
        if (list != null) {
            list.remove(out);
            if (list.isEmpty()) {
                roomClients.remove(room);
            }
            ServerStats.setActiveRooms(new ArrayList<>(roomClients.keySet()));
        }
    }

    public static void broadcast(String room, String message, PrintWriter exclude) {
        CopyOnWriteArrayList<PrintWriter> list = roomClients.get(room);
        if (list != null) {
            for (PrintWriter peer : list) {
                if (peer != exclude) {
                    peer.println(message);
                }
            }
        }
    }

    public static boolean hasRoom(String room) {
        return roomClients.containsKey(room);
    }

    public static void shutdownServer() {
        System.out.println("[Server] shutdownServer() called");
        try {
            if (scheduler != null) {
                scheduler.shutdownNow();
                System.out.println("[Server] scheduler shut down");
            }
            if (executor != null) {
                executor.shutdownNow();
                System.out.println("[Server] executor shut down");
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("[Server] serverSocket closed");
            }
        } catch (IOException e) {
            System.err.println("[Server] Error during shutdown: " + e.getMessage());
        }
    }

    public static void main(String[] args) {


        DataStore store = new DataStore();
        MessageHelper.initialize(store);
        try {
            MessageHelper.backupHistory();
            String msg1 = "[Server] Chat history loaded.";
            System.out.println(msg1);
            ServerStats.addLog(msg1);
        } catch (Exception e) {
            System.err.println("[Server] History load error: " + e.getMessage());
        }

        executor  = Executors.newCachedThreadPool();
        scheduler = Executors.newScheduledThreadPool(1);

        // Periodic backup
        scheduler.scheduleAtFixedRate(() -> {
            try {
                MessageHelper.backupHistory();
                String msg2 = "[Scheduler] History backup completed.";
                System.out.println(msg2);
                ServerStats.addLog(msg2);
            } catch (Exception e) {
                String err = "[Scheduler] Backup error: " + e.getMessage();
                System.err.println(err);
                ServerStats.addLog(err);
            }
            ServerStats.setActiveRooms(new ArrayList<>(roomClients.keySet()));
        }, 1, 1, TimeUnit.MINUTES);


        String startMsg = "ChatServer started on port " + PORT;
        System.out.println(startMsg);
        ServerStats.addLog(startMsg);

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Server] Shutdown hook triggered.");
            shutdownServer();
        }));

        // Accept loop
    try {
            serverSocket = new ServerSocket(PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                ServerStats.clientConnected();
                executor.execute(() -> {
                    ClientHandler handler = new ClientHandler(clientSocket, store);
                    try {
                        handler.run();
                    } finally {
                        ServerStats.clientDisconnected();
                        String room = handler.getCurrentRoom();
                        PrintWriter out = handler.getWriter();
                        if (room != null && out != null) {
                            CopyOnWriteArrayList<PrintWriter> list = roomClients.get(room);
                            if (list != null) {
                                list.remove(out);
                                if (list.isEmpty()) {
                                    roomClients.remove(room);
                                }
                                ServerStats.setActiveRooms(new ArrayList<>(roomClients.keySet()));
                            }
                        }
                    }
                });
            }
        }catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
