package com.flux.service;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.flux.model.ChannelStats;
import com.flux.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Production-grade message persistence service using prepared statements
 * and async execution patterns.
 */
public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    
    private final CqlSession session;
    private final PreparedStatement insertStmt;
    private final PreparedStatement selectLatestStmt;
    private final PreparedStatement countByChannelStmt;

    public MessageService(ScyllaConnection connection) {
        this.session = connection.getSession();
        
        // Prepare statements once during initialization
        this.insertStmt = session.prepare(
            "INSERT INTO flux.messages (channel_id, message_id, user_id, content, created_at) " +
            "VALUES (?, now(), ?, ?, toTimestamp(now()))"
        );
        
        this.selectLatestStmt = session.prepare(
            "SELECT channel_id, message_id, user_id, content, created_at " +
            "FROM flux.messages " +
            "WHERE channel_id = ? " +
            "LIMIT ?"
        );
        
        this.countByChannelStmt = session.prepare(
            "SELECT COUNT(*) as count FROM flux.messages WHERE channel_id = ?"
        );
        
        logger.info("MessageService initialized with prepared statements");
    }

    /**
     * Insert a message asynchronously.
     * Returns CompletionStage for non-blocking execution.
     */
    public CompletionStage<Void> insertMessageAsync(Message message) {
        BoundStatement bound = insertStmt.bind(
            message.channelId(),
            message.userId(),
            message.content()
        );
        
        return session.executeAsync(bound)
            .thenAccept(rs -> {
                if (!rs.wasApplied()) {
                    logger.warn("Insert not applied for message: {}", message.messageId());
                }
            })
            .exceptionally(error -> {
                logger.error("Failed to insert message: {}", error.getMessage());
                return null;
            });
    }

    /**
     * Insert message synchronously (uses Virtual Thread internally if called from one).
     */
    public void insertMessage(Message message) {
        BoundStatement bound = insertStmt.bind(
            message.channelId(),
            message.userId(),
            message.content()
        );
        
        session.execute(bound);
    }

    /**
     * Retrieve latest N messages from a channel.
     * Efficient due to clustering key ordering.
     */
    public List<Message> getLatestMessages(long channelId, int limit) {
        BoundStatement bound = selectLatestStmt.bind(channelId, limit);
        ResultSet rs = session.execute(bound);
        
        List<Message> messages = new ArrayList<>();
        for (Row row : rs) {
            messages.add(new Message(
                row.getLong("channel_id"),
                row.getUuid("message_id"),
                row.getLong("user_id"),
                row.getString("content"),
                row.getInstant("created_at")
            ));
        }
        
        return messages;
    }

    /**
     * Get message count for a channel.
     * NOTE: COUNT(*) is expensive in Cassandra - avoid in production at scale.
     */
    public long getMessageCount(long channelId) {
        BoundStatement bound = countByChannelStmt.bind(channelId);
        Row row = session.execute(bound).one();
        return row != null ? row.getLong("count") : 0;
    }

    /**
     * Get statistics for a channel.
     */
    public ChannelStats getChannelStats(long channelId) {
        long count = getMessageCount(channelId);
        
        // Simplified stats - in production, maintain separate aggregation table
        return new ChannelStats(
            channelId,
            count,
            count * 500, // Estimated bytes (avg message ~500 bytes)
            500.0
        );
    }
}
