package org.example;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ChatServer listens for client connections and schedules backups + graceful shutdown.
 */
public class Server {
    private static final int PORT = 9999;

    public static void main(String[] args) {


        DataStore store = new DataStore();
        MessageHelper.initialize(store);
        try {
            MessageHelper.loadHistory();
            String msg1 = "[Server] Chat history loaded.";
            System.out.println(msg1);
            ServerStats.addLog(msg1);
        } catch (Exception e) {
            System.err.println("[Server] History load error: " + e.getMessage());
        }

        ExecutorService pool = Executors.newCachedThreadPool();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // Periodic backup
        scheduler.scheduleAtFixedRate(() -> {
            try {
                String msg2 = "[Scheduler] History backup completed.";
                System.out.println(msg2);
                ServerStats.addLog(msg2);
            } catch (Exception e) {
                String err = "[Scheduler] Backup error: " + e.getMessage();
                System.err.println(err);
                ServerStats.addLog(err);
            }
            ServerStats.setActiveRooms(store.listRooms());
        }, 1, 1, TimeUnit.MINUTES);


        String startMsg = "ChatServer started on port " + PORT;
        System.out.println(startMsg);
        ServerStats.addLog(startMsg);

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Server] Shutdown hook triggered: terminating services...");
            pool.shutdown();
            scheduler.shutdown();
        }));

        // Accept loop
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("ChatServer started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();

                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                ServerStats.clientConnected();
                pool.execute(() -> {
                     try {
                         new ClientHandler(clientSocket, store).run();
                     } finally {

                        ServerStats.clientDisconnected();
                     }
                 });
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
