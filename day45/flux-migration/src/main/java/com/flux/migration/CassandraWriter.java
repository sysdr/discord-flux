package com.flux.migration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cassandra writer with backpressure control via Semaphore.
 * Uses Virtual Threads (Java 21) for massive concurrency without OS thread exhaustion.
 */
public class CassandraWriter implements AutoCloseable {
    
    private static final Logger log = LoggerFactory.getLogger(CassandraWriter.class);
    
    private final CqlSession session;
    private final PreparedStatement insertStmt;
    private final Semaphore writeSemaphore;
    private final AtomicLong writtenCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    public CassandraWriter(CqlSession session, int maxConcurrentWrites) {
        this.session = session;
        this.writeSemaphore = new Semaphore(maxConcurrentWrites);
        
        // Prepared statement is compiled once, reused for all writes
        this.insertStmt = session.prepare(
            """
            INSERT INTO flux.messages (id, channel_id, user_id, content, timestamp)
            VALUES (?, ?, ?, ?, ?)
            """
        );
        
        log.info("CassandraWriter initialized with {} concurrent write permits", maxConcurrentWrites);
    }
    
    /**
     * Non-blocking write using Virtual Thread.
     * Semaphore provides backpressure - blocks if maxConcurrentWrites in flight.
     */
    public void write(Message msg) {
        try {
            writeSemaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Write interrupted", e);
        }
        
        // Virtual Thread handles blocking I/O efficiently
        Thread.startVirtualThread(() -> {
            try {
                BoundStatement bound = insertStmt.bind(
                    msg.id(),
                    msg.channelId(),
                    msg.userId(),
                    msg.content(),
                    msg.timestamp()
                );
                
                session.execute(bound);
                writtenCount.incrementAndGet();
                
            } catch (Exception e) {
                log.error("Failed to write message {}: {}", msg.id(), e.getMessage());
                errorCount.incrementAndGet();
            } finally {
                writeSemaphore.release();
            }
        });
    }
    
    public long getWrittenCount() {
        return writtenCount.get();
    }
    
    public long getErrorCount() {
        return errorCount.get();
    }
    
    public int getActiveWrites() {
        return writeSemaphore.availablePermits();
    }
    
    @Override
    public void close() {
        // Wait for all in-flight writes to complete
        log.info("Waiting for {} in-flight writes to complete...", 
                 writeSemaphore.availablePermits());
        
        try {
            // Acquire all permits = all writes done
            writeSemaphore.acquire(writeSemaphore.availablePermits());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("All writes completed. Written: {}, Errors: {}", 
                 writtenCount.get(), errorCount.get());
    }
}
