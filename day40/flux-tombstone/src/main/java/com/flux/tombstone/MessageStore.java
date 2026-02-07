package com.flux.tombstone;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class MessageStore {
    
    private final ConcurrentSkipListMap<MessageId, Object> memTable = new ConcurrentSkipListMap<>();
    private final CopyOnWriteArrayList<Map<MessageId, Object>> sstables = new CopyOnWriteArrayList<>();
    
    private static final int FLUSH_THRESHOLD = 1000;
    private final AtomicLong insertCount = new AtomicLong();
    private final AtomicLong deleteCount = new AtomicLong();
    
    private volatile boolean running = true;
    
    public MessageStore() {
        startCompactionLoop();
    }
    
    public void insert(Message msg) {
        memTable.put(msg.id(), msg);
        insertCount.incrementAndGet();
        
        if (memTable.size() > FLUSH_THRESHOLD) {
            flushMemTable();
        }
    }
    
    public void delete(MessageId id) {
        memTable.put(id, new Tombstone(id));
        deleteCount.incrementAndGet();
    }
    
    public Optional<Message> read(MessageId id) {
        // Check memTable first
        Object value = memTable.get(id);
        if (value instanceof Tombstone) {
            return Optional.empty();
        }
        if (value instanceof Message msg) {
            return Optional.of(msg);
        }
        
        // Scan SSTables from newest to oldest
        for (int i = sstables.size() - 1; i >= 0; i--) {
            value = sstables.get(i).get(id);
            if (value instanceof Tombstone) {
                return Optional.empty();
            }
            if (value instanceof Message msg) {
                return Optional.of(msg);
            }
        }
        
        return Optional.empty();
    }
    
    public List<Message> scan(String channelId, int limit) {
        List<Message> results = new ArrayList<>();
        Set<MessageId> seen = new HashSet<>();
        
        // Merge memTable + all SSTables
        List<Map<MessageId, Object>> allTables = new ArrayList<>();
        allTables.add(memTable);
        allTables.addAll(sstables);
        
        for (var table : allTables) {
            for (var entry : table.entrySet()) {
                if (seen.contains(entry.getKey())) continue;
                seen.add(entry.getKey());
                
                Object value = entry.getValue();
                if (value instanceof Message msg && msg.channelId().equals(channelId)) {
                    results.add(msg);
                    if (results.size() >= limit) break;
                }
            }
            if (results.size() >= limit) break;
        }
        
        results.sort((a, b) -> b.id().compareTo(a.id())); // Newest first
        return results.subList(0, Math.min(limit, results.size()));
    }
    
    private void flushMemTable() {
        if (memTable.isEmpty()) return;
        
        Map<MessageId, Object> snapshot = new TreeMap<>(memTable);
        sstables.add(Collections.unmodifiableMap(snapshot));
        memTable.clear();
        
        System.out.printf("[FLUSH] Created SSTable #%d with %d entries%n", 
            sstables.size(), snapshot.size());
    }
    
    public void forceCompaction() {
        compact();
    }
    
    private void compact() {
        if (sstables.size() < 2) return;
        
        long start = System.nanoTime();
        
        // Merge all SSTables
        Map<MessageId, Object> merged = new TreeMap<>();
        for (var sstable : sstables) {
            for (var entry : sstable.entrySet()) {
                merged.merge(entry.getKey(), entry.getValue(), (old, newer) -> {
                    // Keep the value with the latest timestamp
                    long oldTs = getTimestamp(old);
                    long newTs = getTimestamp(newer);
                    return newTs > oldTs ? newer : old;
                });
            }
        }
        
        // Remove tombstones older than TTL (7 days)
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        int tombstonesRemoved = 0;
        var iterator = merged.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue() instanceof Tombstone t && t.deletedAt() < cutoff) {
                iterator.remove();
                tombstonesRemoved++;
            }
        }
        
        // Replace all SSTables with compacted version
        int oldCount = sstables.size();
        sstables.clear();
        if (!merged.isEmpty()) {
            sstables.add(Collections.unmodifiableMap(merged));
        }
        
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.printf("[COMPACTION] %d → %d SSTables | Removed %d tombstones | %d ms%n",
            oldCount, sstables.size(), tombstonesRemoved, elapsed);
    }
    
    private long getTimestamp(Object value) {
        return switch (value) {
            case Message m -> m.createdAt();
            case Tombstone t -> t.deletedAt();
            default -> 0L;
        };
    }
    
    private void startCompactionLoop() {
        Thread.ofVirtual().name("compaction-worker").start(() -> {
            while (running) {
                try {
                    Thread.sleep(Duration.ofSeconds(30));
                    compact();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }
    
    public void shutdown() {
        running = false;
    }
    
    public Stats getStats() {
        int activeMessages = 0;
        int tombstones = 0;
        
        Set<MessageId> allKeys = new HashSet<>();
        allKeys.addAll(memTable.keySet());
        sstables.forEach(table -> allKeys.addAll(table.keySet()));
        
        for (var id : allKeys) {
            var value = read(id);
            if (value.isPresent()) {
                activeMessages++;
            } else {
                // Check if tombstone exists
                if (memTable.get(id) instanceof Tombstone || 
                    sstables.stream().anyMatch(t -> t.get(id) instanceof Tombstone)) {
                    tombstones++;
                }
            }
        }
        
        return new Stats(activeMessages, tombstones, sstables.size(), 
            insertCount.get(), deleteCount.get());
    }
    
    /** Returns up to limit message IDs for demo deletion (not tombstones) */
    public List<MessageId> getSampleMessageIds(int limit) {
        List<MessageId> result = new ArrayList<>();
        Set<MessageId> seen = new HashSet<>();
        List<Map<MessageId, Object>> allTables = new ArrayList<>();
        allTables.add(memTable);
        allTables.addAll(sstables);
        for (var table : allTables) {
            for (var entry : table.entrySet()) {
                if (entry.getValue() instanceof Message) {
                    if (!seen.contains(entry.getKey())) {
                        seen.add(entry.getKey());
                        result.add(entry.getKey());
                        if (result.size() >= limit) return result;
                    }
                }
            }
        }
        return result;
    }
    
    public record Stats(int activeMessages, int tombstones, int sstableCount,
                        long totalInserts, long totalDeletes) {}
}
