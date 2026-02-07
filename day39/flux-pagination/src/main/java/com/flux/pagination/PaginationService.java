package com.flux.pagination;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.flux.model.Message;
import com.flux.model.PageResult;
import com.flux.model.PaginationCursor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PaginationService {
    
    private final CqlSession session;
    private final PreparedStatement queryInitial;
    private final PreparedStatement queryNext;
    private final PreparedStatement queryPrevious;
    private final AtomicLong totalQueries = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    
    public PaginationService(CqlSession session) {
        this.session = session;
        
        // Initial page (no cursor)
        this.queryInitial = session.prepare(
            "SELECT * FROM flux.messages " +
            "WHERE channel_id = ? " +
            "ORDER BY message_id DESC " +
            "LIMIT ?"
        );
        
        // Next page (cursor-based, descending)
        this.queryNext = session.prepare(
            "SELECT * FROM flux.messages " +
            "WHERE channel_id = ? AND message_id < ? " +
            "ORDER BY message_id DESC " +
            "LIMIT ?"
        );
        
        // Previous page (cursor-based, ascending)
        this.queryPrevious = session.prepare(
            "SELECT * FROM flux.messages " +
            "WHERE channel_id = ? AND message_id > ? " +
            "ORDER BY message_id ASC " +
            "LIMIT ?"
        );
    }
    
    public PageResult fetchPage(long channelId, String cursorToken, int limit, Direction direction) {
        long startTime = System.currentTimeMillis();
        
        BoundStatement bound;
        if (cursorToken == null || cursorToken.isBlank()) {
            // Initial page
            bound = queryInitial.bind(channelId, limit + 1);
        } else {
            PaginationCursor cursor = PaginationCursor.decode(cursorToken);
            if (direction == Direction.NEXT) {
                // Next page (older messages)
                bound = queryNext.bind(channelId, cursor.messageId(), limit + 1);
            } else {
                // Previous page (newer messages)
                bound = queryPrevious.bind(channelId, cursor.messageId(), limit + 1);
            }
        }
        
        ResultSet rs = session.execute(bound);
        Iterator<Row> iterator = rs.iterator();
        
        List<Message> messages = new ArrayList<>(limit);
        int count = 0;
        
        while (iterator.hasNext() && count < limit) {
            Row row = iterator.next();
            messages.add(new Message(
                row.getLong("message_id"),
                row.getLong("channel_id"),
                row.getLong("author_id"),
                row.getString("content"),
                row.getInstant("created_at")
            ));
            count++;
        }
        
        boolean hasMore = iterator.hasNext();
        
        // For previous page queries, reverse the list to maintain chronological order
        if (direction == Direction.PREVIOUS && !messages.isEmpty()) {
            Collections.reverse(messages);
        }
        
        String nextCursor = null;
        String previousCursor = null;
        
        if (!messages.isEmpty()) {
            if (direction == Direction.NEXT || cursorToken == null) {
                // Set next cursor if there are more messages
                if (hasMore) {
                    Message lastMsg = messages.get(messages.size() - 1);
                    nextCursor = new PaginationCursor(lastMsg.messageId(), System.currentTimeMillis()).encode();
                }
                // Set previous cursor if this is not the first page
                if (cursorToken != null) {
                    Message firstMsg = messages.get(0);
                    previousCursor = new PaginationCursor(firstMsg.messageId(), System.currentTimeMillis()).encode();
                }
            } else {
                // Direction.PREVIOUS
                Message firstMsg = messages.get(0);
                previousCursor = new PaginationCursor(firstMsg.messageId(), System.currentTimeMillis()).encode();
                
                if (!messages.isEmpty()) {
                    Message lastMsg = messages.get(messages.size() - 1);
                    nextCursor = new PaginationCursor(lastMsg.messageId(), System.currentTimeMillis()).encode();
                }
            }
        }
        
        long latency = System.currentTimeMillis() - startTime;
        totalQueries.incrementAndGet();
        totalLatencyMs.addAndGet(latency);
        
        return new PageResult(messages, nextCursor, previousCursor, hasMore, count, latency);
    }
    
    public double getAverageLatencyMs() {
        long queries = totalQueries.get();
        return queries == 0 ? 0.0 : (double) totalLatencyMs.get() / queries;
    }
    
    public long getTotalQueries() {
        return totalQueries.get();
    }
    
    public enum Direction {
        NEXT, PREVIOUS
    }
}
