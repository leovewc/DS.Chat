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

public class ReplicaNode {
    private final String host;
    private final int port;

    public ReplicaNode(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // 将日志条目发送到从节点
    public void sendLog(String roomId, String messageJson) {
        try (Socket socket = new Socket(host, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            // 格式：roomId|messageJson
            out.println(roomId + "|" + messageJson);
        } catch (IOException e) {
            System.err.println("Failed to replicate to " + host + ":" + port + ": " + e.getMessage());
        }
    }
}