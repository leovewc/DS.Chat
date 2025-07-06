package org.example;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;

// 3. 新增 ReplicationServer.java，在从节点启动时监听 Leader 推送
public class ReplicationServer {
    private final DataStore store;
    private final int port;

    public ReplicationServer(DataStore store, int port) {
        this.store = store;
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("ReplicationServer listening on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                // 异步处理接收
                CompletableFuture.runAsync(() -> process(socket));
            }
        } catch (IOException e) {
            System.err.println("ReplicationServer error: " + e.getMessage());
        }
    }

    private void process(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                // 按分隔符拆分 roomId 与 messageJson
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) {
                    String roomId = parts[0];
                    String messageJson = parts[1];
                    // 将日志应用到本地存储
                    store.addMessage(roomId, messageJson);
                    System.out.println("Replicated message to room " + roomId + ": " + messageJson);
                }
            }
        } catch (IOException e) {
            System.err.println("ReplicationServer process error: " + e.getMessage());
        }
    }
}