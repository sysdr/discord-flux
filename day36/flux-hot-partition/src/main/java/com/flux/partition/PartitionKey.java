package com.flux.partition;

import com.flux.generator.SnowflakeGenerator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a partition key in the wide-column store.
 * Combines channel ID with a time bucket for distributed partitioning.
 */
public record PartitionKey(long channelId, String timeBucket) implements Comparable<PartitionKey> {

    /**
     * Create a partition key from a message using the specified bucketing strategy.
     */
    public static PartitionKey fromMessage(long channelId, long snowflakeId, BucketStrategy strategy) {
        long timestamp = SnowflakeGenerator.getTimestamp(snowflakeId);
        String bucket = strategy.computeBucket(timestamp);
        return new PartitionKey(channelId, bucket);
    }

    /**
     * Create a naive partition key (channel ID only - BAD for production).
     */
    public static PartitionKey naive(long channelId) {
        return new PartitionKey(channelId, "ALL");
    }

    @Override
    public int compareTo(PartitionKey other) {
        int channelCompare = Long.compare(this.channelId, other.channelId);
        if (channelCompare != 0) {
            return channelCompare;
        }
        return this.timeBucket.compareTo(other.timeBucket);
    }

    @Override
    public String toString() {
        return "(%d, %s)".formatted(channelId, timeBucket);
    }
}
