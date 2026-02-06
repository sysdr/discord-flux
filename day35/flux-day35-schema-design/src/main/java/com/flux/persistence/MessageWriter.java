package com.flux.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;

public class MessageWriter {
    private final CqlSession session;
    private final PreparedStatement insertStmt;
    private final PreparedStatement updateMetricsStmt;
    private final LongAdder writeCounter = new LongAdder();

    public MessageWriter(CqlSession session) {
        this.session = session;
        this.insertStmt = session.prepare(
            "INSERT INTO messages (channel_id, bucket, message_id, user_id, content, created_at) VALUES (?, ?, ?, ?, ?, ?)");
        this.updateMetricsStmt = session.prepare(
            "UPDATE partition_metrics SET message_count = message_count + 1 WHERE channel_id = ? AND bucket = ?");
    }

    public CompletableFuture<Void> writeMessage(Message msg) {
        int bucket = MessagePartition.hourlyBucket(msg.timestamp());
        var messageId = Uuids.timeBased();
        var insertFuture = session.executeAsync(insertStmt.bind(
            msg.channelId(), bucket, messageId, msg.userId(), msg.content(),
            Instant.ofEpochMilli(msg.timestamp()))).toCompletableFuture();
        var metricsFuture = session.executeAsync(updateMetricsStmt.bind(msg.channelId(), bucket)).toCompletableFuture();
        writeCounter.increment();
        return CompletableFuture.allOf(insertFuture, metricsFuture);
    }

    public long getWriteCount() { return writeCounter.sum(); }
}
