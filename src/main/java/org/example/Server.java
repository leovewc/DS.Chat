package org.example;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatServer listens for client connections, schedules backups, graceful shutdown,
 * and supports Master–Follower replication.
 */
public class Server {
    private static final int PORT = 9999;
    private static final int REPLICATION_PORT = 10001;  // 本节点作为从节点的监听端口

    private static ServerSocket serverSocket;
    private static ExecutorService executor;
    private static ScheduledExecutorService scheduler;

    // 存储房间到客户端输出流的映射
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<PrintWriter>> roomClients = new ConcurrentHashMap<>();
    // 主从复制的从节点列表
    private static final List<ReplicaNode> replicas = new CopyOnWriteArrayList<>();

    public static final Map<String, CopyOnWriteArrayList<String>> usersInRoom = new ConcurrentHashMap<>();


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

    /**
     * 广播消息到房间内所有客户端，并复制到所有配置的从节点
     */
    public static void broadcast(String room, String message, PrintWriter exclude) {
        // 1) 本地广播
        CopyOnWriteArrayList<PrintWriter> list = roomClients.get(room);
        if (list != null) {
            for (PrintWriter peer : list) {
                if (peer != exclude) {
                    peer.println(message);
                }
            }
        }
        // 2) 异步推送日志到所有从节点
        for (ReplicaNode replica : replicas) {
            CompletableFuture.runAsync(() -> replica.sendLog(room, message));
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

        // 如果启动参数中带从节点配置：java -jar dschat.jar leader [host1 port1 host2 port2 ...]
        if (args.length >= 3 && "leader".equals(args[0])) {
            for (int i = 1; i < args.length - 1; i += 2) {
                String host = args[i];
                int port = Integer.parseInt(args[i + 1]);
                replicas.add(new ReplicaNode(host, port));
            }
        }

        // 启动本节点的 ReplicationServer，作为从节点接收 Leader 推送
        new Thread(() -> {
            ReplicationServer repServer = new ReplicationServer(store, REPLICATION_PORT);
            repServer.start();
        }).start();

        try {
            MessageHelper.backupHistory();
            String msg1 = "[Server] Chat history loaded.";
            System.out.println(msg1);
            ServerStats.addLog(msg1);
        } catch (Exception e) {
            System.err.println("[Server] History load error: " + e.getMessage());
        }

        executor = Executors.newCachedThreadPool();
        scheduler = Executors.newScheduledThreadPool(1);

        // 定时备份聊天历史
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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Server] Shutdown hook triggered.");
            shutdownServer();
        }));

        // 接收客户端连接循环
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
                            unregisterClient(room, out);
                        }
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}