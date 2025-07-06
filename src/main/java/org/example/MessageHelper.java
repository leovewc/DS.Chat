package org.example;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.util.List;

/**
 * MessageHelper provides CSV-based persistence for chat history.
 */
public class MessageHelper {
    private static DataStore store;
    private static final String HISTORY_FILE = "chat_history.csv";
    private static final String BACKUP_DIR = "backups/";

    /** Bind the shared DataStore */
    public static void initialize(DataStore dataStore) {
        store = dataStore;
    }

    /** Load existing history into memory (skips if no file) */
    public static void loadHistory() throws IOException {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) {
            System.err.println("[MessageHelper] History file not found: skipping load");
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // CSV format: "room",timestamp,"message"
                String[] parts = line.split("\",\"");
                if (parts.length >= 3) {
                    String room = parts[0].replaceFirst("^\"", "");
                    String message = parts[2].replaceFirst("\"$", "");
                    store.addMessage(room, message);
                }
            }
        }
    }

    /** Backup in-memory history into timestamped CSV, ensuring directory exists */
    public static void backupHistory() throws IOException {
        new File(BACKUP_DIR).mkdirs();
        String backupFile = BACKUP_DIR + "history_" + System.currentTimeMillis() + ".csv";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(backupFile))) {
            for (String room : store.listRooms()) {
                List<String> msgs = store.getRecentMessages(room, Integer.MAX_VALUE);
                for (String msg : msgs) {
                    // escape quotes, wrap in quotes
                    String escRoom = room.replace("\"", "\"\"");
                    String escMsg = msg.replace("\"", "\"\"");
                    writer.write("\"" + escRoom + "\"," + System.currentTimeMillis() + ",\"" + escMsg + "\"\n");
                }
            }
        }
    }

    /** Append one message to main history file, creating directories if needed */
    public static void appendMessage(String room, String message) throws IOException {
        File file = new File(HISTORY_FILE);
        File dir = file.getParentFile();
        if (dir != null) dir.mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            String escRoom = room.replace("\"", "\"\"");
            String escMsg = message.replace("\"", "\"\"");
            writer.write("\"" + escRoom + "\"," + System.currentTimeMillis() + ",\"" + escMsg + "\"\n");
        }
    }
}
