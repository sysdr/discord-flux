package com.flux.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import java.util.ArrayList;
import java.util.List;

public class MessageReader {
    private final CqlSession session;
    private final PreparedStatement queryStmt;

    public MessageReader(CqlSession session) {
        this.session = session;
        this.queryStmt = session.prepare(
            "SELECT channel_id, message_id, user_id, content, created_at FROM messages WHERE channel_id = ? AND bucket = ? LIMIT ?");
    }

    public List<Message> fetchLatestMessages(long channelId, int limit) {
        int currentBucket = MessagePartition.hourlyBucket(System.currentTimeMillis());
        var messages = new ArrayList<Message>();
        messages.addAll(queryBucket(channelId, currentBucket, limit));
        for (int i = 1; messages.size() < limit && i < 24; i++) {
            currentBucket = MessagePartition.decrementBucket(currentBucket);
            messages.addAll(queryBucket(channelId, currentBucket, limit - messages.size()));
        }
        return messages.subList(0, Math.min(messages.size(), limit));
    }

    private List<Message> queryBucket(long channelId, int bucket, int limit) {
        var messages = new ArrayList<Message>();
        for (Row row : session.execute(queryStmt.bind(channelId, bucket, limit))) {
            messages.add(new Message(row.getLong("channel_id"), row.getUuid("message_id"),
                row.getLong("user_id"), row.getString("content"), row.getInstant("created_at").toEpochMilli()));
        }
        return messages;
    }
}
