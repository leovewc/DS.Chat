package org.example;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.ArrayList;

/**
 * DataStore manages chat rooms and their messages with thread-safe access.
 */
public class DataStore {
    private final ConcurrentHashMap<String, List<String>> rooms = new ConcurrentHashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Ensure a room exists.
     */
    public void createRoom(String room) {
        rwLock.writeLock().lock();
        try {
            rooms.putIfAbsent(room, new ArrayList<>());
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Add a message to the specified room.
     * Always done under write-lock to avoid concurrent List modification.
     */
    public void addMessage(String room, String message) {
        createRoom(room);
        rwLock.writeLock().lock();
        try {
            rooms.get(room).add(message);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Get a snapshot of recent messages from a room.
     */
    public List<String> getRecentMessages(String room, int count) {
        rwLock.readLock().lock();
        try {
            List<String> msgs = rooms.getOrDefault(room, new ArrayList<>());
            int size = msgs.size();
            int from = Math.max(0, size - count);
            return new ArrayList<>(msgs.subList(from, size));
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Get list of active rooms.
     */
    public List<String> listRooms() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(rooms.keySet());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Remove an empty or no-longer-used room.
     */
    public void removeRoom(String room) {
        rwLock.writeLock().lock();
        try {
            rooms.remove(room);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
