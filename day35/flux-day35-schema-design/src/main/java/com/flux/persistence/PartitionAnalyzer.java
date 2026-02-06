package com.flux.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import java.util.*;

public class PartitionAnalyzer {
    private final CqlSession session;

    public PartitionAnalyzer(CqlSession session) { this.session = session; }

    public record PartitionStats(long channelId, int bucket, long messageCount) {}

    public List<PartitionStats> analyzePartitions(long channelId) {
        var stmt = session.prepare("SELECT channel_id, bucket, message_count FROM partition_metrics WHERE channel_id = ?");
        var stats = new ArrayList<PartitionStats>();
        for (Row row : session.execute(stmt.bind(channelId))) {
            stats.add(new PartitionStats(row.getLong("channel_id"), row.getInt("bucket"), row.getLong("message_count")));
        }
        return stats;
    }
}
