package com.flux.storage;

import com.flux.compaction.CompactionManager;
import com.flux.model.Message;
import com.flux.model.SnowflakeId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StorageEngine {
    private final Path dataDir;
    private final CompactionManager compactionManager;
    private MemTable activeMemTable;
    private final List<SSTable> sstables = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public StorageEngine(Path dataDir, CompactionManager compactionManager) throws IOException {
        this.dataDir = dataDir;
        this.compactionManager = compactionManager;
        this.activeMemTable = new MemTable();
        Files.createDirectories(dataDir);
    }
    
    public void write(Message message) throws IOException {
        lock.readLock().lock();
        try {
            if (!activeMemTable.put(message)) {
                // MemTable is frozen, need to flush
                lock.readLock().unlock();
                flush();
                lock.readLock().lock();
                activeMemTable.put(message);
            }
            
            if (activeMemTable.shouldFlush()) {
                lock.readLock().unlock();
                flush();
                lock.readLock().lock();
            }
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public Message read(SnowflakeId id) throws IOException {
        lock.readLock().lock();
        try {
            // Check active MemTable first
            Message msg = activeMemTable.get(id);
            if (msg != null) return msg;
            
            // Check SSTables (newest to oldest)
            for (int i = sstables.size() - 1; i >= 0; i--) {
                msg = sstables.get(i).get(id);
                if (msg != null) return msg;
            }
            
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void flush() throws IOException {
        lock.writeLock().lock();
        try {
            if (activeMemTable.messageCount() == 0) {
                return;
            }
            
            activeMemTable.freeze();
            SSTable sst = SSTable.write(dataDir, activeMemTable.getSnapshot());
            sstables.add(sst);
            activeMemTable = new MemTable();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void compact() throws IOException {
        lock.writeLock().lock();
        try {
            List<SSTable> compacted = compactionManager.compact(sstables);
            sstables.clear();
            sstables.addAll(compacted);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void deleteExpired() throws IOException {
        lock.writeLock().lock();
        try {
            compactionManager.deleteExpired(sstables);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public int sstableCount() {
        lock.readLock().lock();
        try {
            return sstables.size();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public List<SSTable> getSSTableSnapshot() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(sstables);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public CompactionManager getCompactionManager() {
        return compactionManager;
    }
}
