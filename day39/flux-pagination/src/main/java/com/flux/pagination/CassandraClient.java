package com.flux.pagination;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.flux.model.Message;
import com.flux.generator.SnowflakeIdGenerator;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CassandraClient implements AutoCloseable {
    
    private final CqlSession session;
    private final SnowflakeIdGenerator idGenerator;
    private final PreparedStatement insertStmt;
    
    public CassandraClient(String contactPoint, int port) {
        this.session = CqlSession.builder()
            .addContactPoint(new InetSocketAddress(contactPoint, port))
            .withLocalDatacenter("datacenter1")
            .build();
        
        this.idGenerator = new SnowflakeIdGenerator(1, 1);
        
        // Prepare statements
        this.insertStmt = session.prepare(
            "INSERT INTO flux.messages (channel_id, message_id, author_id, content, created_at) " +
            "VALUES (?, ?, ?, ?, ?)"
        );
        
        System.out.println("✅ Connected to Cassandra at " + contactPoint + ":" + port);
    }
    
    public void initializeSchema() {
        // Create keyspace
        session.execute(
            "CREATE KEYSPACE IF NOT EXISTS flux " +
            "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}"
        );
        
        // Create table
        session.execute(
            "CREATE TABLE IF NOT EXISTS flux.messages (" +
            "channel_id bigint, " +
            "message_id bigint, " +
            "author_id bigint, " +
            "content text, " +
            "created_at timestamp, " +
            "PRIMARY KEY (channel_id, message_id)) " +
            "WITH CLUSTERING ORDER BY (message_id DESC)"
        );
        
        System.out.println("✅ Schema initialized");
    }
    
    public Message insertMessage(long channelId, long authorId, String content) {
        long messageId = idGenerator.nextId();
        Instant now = Instant.now();
        
        session.execute(insertStmt.bind(channelId, messageId, authorId, content, now));
        
        return new Message(messageId, channelId, authorId, content, now);
    }
    
    public void bulkInsert(long channelId, int count) {
        System.out.println("📝 Inserting " + count + " messages into channel " + channelId + "...");
        BatchStatement batch = BatchStatement.newInstance(BatchType.UNLOGGED);
        
        for (int i = 0; i < count; i++) {
            long messageId = idGenerator.nextId();
            long authorId = ThreadLocalRandom.current().nextLong(1000, 9999);
            String content = "Message " + i + " - " + generateRandomContent();
            
            batch = batch.add(insertStmt.bind(channelId, messageId, authorId, content, Instant.now()));
            
            if (batch.size() >= 100) {
                session.execute(batch);
                batch = BatchStatement.newInstance(BatchType.UNLOGGED);
            }
            
            if (i % 10000 == 0 && i > 0) {
                System.out.println("  Progress: " + i + "/" + count);
            }
        }
        
        if (batch.size() > 0) {
            session.execute(batch);
        }
        
        System.out.println("✅ Bulk insert complete");
    }
    
    private String generateRandomContent() {
        String[] words = {"Hello", "World", "Flux", "Cassandra", "Pagination", "Cursor", "Message", "Scale"};
        int length = ThreadLocalRandom.current().nextInt(3, 10);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(words[ThreadLocalRandom.current().nextInt(words.length)]).append(" ");
        }
        return sb.toString().trim();
    }
    
    public CqlSession getSession() {
        return session;
    }
    
    @Override
    public void close() {
        if (session != null) {
            session.close();
            System.out.println("🔌 Cassandra connection closed");
        }
    }
}
