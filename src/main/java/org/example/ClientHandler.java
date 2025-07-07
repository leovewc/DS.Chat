package org.example;

import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientHandler processes commands from a connected chat client.
 * 现在支持 JOIN <room> <username> 并在广播时附带用户名，实现群聊消息气泡功能。
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final DataStore store;
    private String currentRoom;
    private String username;
    private PrintWriter out;
    private BufferedReader in;

    public String getCurrentRoom() { return currentRoom; }
    public PrintWriter getWriter()      { return out;         }

    public ClientHandler(Socket socket, DataStore store) {
        this.socket = socket;
        this.store = store;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("Welcome! Commands: JOIN <room> <username>, SEND <room> <message>, LIST, HISTORY <room> <count>, QUIT");

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(" ", 3);
                String cmd = parts[0].toUpperCase();
                switch (cmd) {
                    case "JOIN":
                    if (parts.length >= 3) {
                        String room = parts[1];
                        String user = parts[2];

                        // —— ① 服务端维护房间用户顺序并分配 avatarId ——
                        Server.usersInRoom
                            .computeIfAbsent(room, r -> new CopyOnWriteArrayList<>());
                        CopyOnWriteArrayList<String> list = Server.usersInRoom.get(room);
                        if (!list.contains(user)) list.add(user);
                        int avatarId = list.indexOf(user);

                        // —— ② 创建房间、注册客户端 ——
                        currentRoom = room;
                        username = user;
                        store.createRoom(room);
                        out.println("Joined room: " + room + " as " + username);
                        ServerStats.addLog("Client " + username + " joined room: " + room);
                        Server.registerClient(room, out);

                        // —— ③ 广播 USERJOIN 通知，包含 avatarId ——
                        String joinMsg = "USERJOIN|" + username + "|" + avatarId;
                        Server.broadcast(room, joinMsg, null);

                        // —— ④ 发送最近 N 条历史消息 ——
                        final int N = 10;
                        List<String> recent = store.getRecentMessages(room, N);
                        if (!recent.isEmpty()) {
                            out.println("Last " + N + " messages in " + room + ":");
                            for (String m : recent) {
                                out.println(m);
                            }
                        }
                    } else {
                        out.println("Usage: JOIN <room> <username>");
                    }
                    break;


                    case "SEND":
                        if (parts.length >= 3 && currentRoom != null && username != null) {
                            String room = parts[1];
                            String msg = parts[2];

                            // 持久化到 DataStore 和备份
                            store.addMessage(room, username + ": " + msg);
                            try {
                                MessageHelper.appendMessage(room, username + ": " + msg);
                            } catch (IOException e) {
                                out.println("Error persisting message: " + e.getMessage());
                            }

                            // 广播给房间内其他客户端，并包含用户名
                            String fullMsg = username + "|" + msg;
                            Server.broadcast(room, fullMsg, out);
                            ServerStats.addLog("Message from " + username + " to " + room + ": " + msg);
                        } else {
                            out.println("Usage: SEND <room> <message> (after JOINing)");
                        }
                        break;

                    case "LIST":
                        out.println("Rooms: " + store.listRooms());
                        break;

                    case "HISTORY":
                        if (parts.length >= 3) {
                            String room = parts[1];
                            int count;
                            try {
                                count = Integer.parseInt(parts[2]);
                            } catch (NumberFormatException e) {
                                out.println("Count must be a number");
                                break;
                            }
                            List<String> history = store.getRecentMessages(room, count);
                            out.println("Last " + count + " messages in " + room + ":");
                            for (String m : history) {
                                out.println(m);
                            }
                        } else {
                            out.println("Usage: HISTORY <room> <count>");
                        }
                        break;

                    case "QUIT":
                        out.println("Goodbye!");
                        ServerStats.addLog("Client " + (username != null ? username : "") + " quit");
                        // 清理客户端注册信息
                        if (currentRoom != null) {
                            Server.unregisterClient(currentRoom, out);
                            if (!Server.hasRoom(currentRoom)) {
                                store.removeRoom(currentRoom);
                            }
                        }
                        socket.close();
                        return;

                    default:
                        out.println("Unknown command.");
                }
            }
        } catch (IOException e) {
            System.err.println("ClientHandler error: " + e.getMessage());
        }
    }
}
