package com.flux.service;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages ScyllaDB connection lifecycle with production-grade pooling.
 * Uses Virtual Threads for blocking operations when executing synchronous queries.
 */
public class ScyllaConnection implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ScyllaConnection.class);
    
    private final CqlSession session;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ScyllaConnection(String contactPoint, int port, String datacenter) {
        logger.info("Connecting to ScyllaDB at {}:{}", contactPoint, port);
        
        this.session = CqlSession.builder()
            .addContactPoint(new InetSocketAddress(contactPoint, port))
            .withLocalDatacenter(datacenter)
            .build();
        
        logger.info("ScyllaDB connection established. Cluster: {}",
            session.getMetadata().getClusterName().orElse("unknown"));
    }

    public void initializeSchema() {
        logger.info("Initializing schema...");
        
        // Create keyspace with replication
        session.execute(SimpleStatement.builder("""
            CREATE KEYSPACE IF NOT EXISTS flux
            WITH replication = {
                'class': 'SimpleStrategy',
                'replication_factor': 1
            }
            """).build());
        
        // Create messages table with partition key and clustering key
        session.execute(SimpleStatement.builder("""
            CREATE TABLE IF NOT EXISTS flux.messages (
                channel_id BIGINT,
                message_id TIMEUUID,
                user_id BIGINT,
                content TEXT,
                created_at TIMESTAMP,
                PRIMARY KEY (channel_id, message_id)
            ) WITH CLUSTERING ORDER BY (message_id DESC)
            """).build());
        
        logger.info("Schema initialized successfully");
    }

    public CqlSession getSession() {
        if (closed.get()) {
            throw new IllegalStateException("Session is closed");
        }
        return session;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            logger.info("Closing ScyllaDB connection...");
            session.close();
        }
    }
}
