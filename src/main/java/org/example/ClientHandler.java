package org.example;

import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.List;

/**
 * ClientHandler processes commands from a connected chat client.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final DataStore store;
    private String currentRoom;
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
            out.println("Welcome! Commands: JOIN <room>, SEND <room> <message>, LIST, HISTORY <room> <count>, QUIT");

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(" ", 3);
                String cmd = parts[0].toUpperCase();
                switch (cmd) {
                    case "JOIN":
                        if (parts.length >= 2) {
                            String room = parts[1];
                            store.createRoom(room);
                            currentRoom = room;
                            out.println("Joined room: " + room);
                            ServerStats.addLog("Client joined room: " + room);
                            Server.registerClient(room, out);

                            // —— 在这里添加：自动拉取 N 条最近消息 ——
                            final int N = 10;  // 你想要的历史条数，改成自己需要的值
                            List<String> recent = store.getRecentMessages(room, N);
                            if (!recent.isEmpty()) {
                                out.println("Last " + N + " messages in " + room + ":");
                                for (String m : recent) {
                                    out.println(m);
                                }
                            }
                        } else {
                            out.println("Usage: JOIN <room>");
                        }

                        break;
                    case "SEND":
                        if (parts.length >= 3) {
                            String room = parts[1];
                            String msg = parts[2];
                            store.addMessage(room, msg);
                            try {
                                MessageHelper.appendMessage(room, msg);
                            } catch (IOException e) {
                                out.println("Error persisting message: " + e.getMessage());
                            }
                            String fullMsg = "[" + room + "] " + msg;
                            Server.broadcast(room, fullMsg, out);
                            out.println("Message sent to " + room);
                            ServerStats.addLog("Message sent to " + room + ": " + msg);
                        } else {
                            out.println("Usage: SEND <room> <message>");
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
                            out.println("Last " + count + " messages in " + room + ": " + store.getRecentMessages(room, count));
                        } else {
                            out.println("Usage: HISTORY <room> <count>");
                        }
                        break;
                    case "QUIT":
                        out.println("Goodbye!");
                        ServerStats.addLog("Client quit" + (currentRoom != null ? " from room: " + currentRoom : ""));
                        // Clean up room when client leaves

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
