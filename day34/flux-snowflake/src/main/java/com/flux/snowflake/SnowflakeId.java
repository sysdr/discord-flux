package com.flux.snowflake;

import java.time.Instant;

/**
 * Parsed representation of a Snowflake ID.
 */
public record SnowflakeId(
    long id,
    long timestamp,
    long workerId,
    long sequence
) {
    public Instant toInstant() {
        return Instant.ofEpochMilli(timestamp);
    }
    
    @Override
    public String toString() {
        return String.format(
            "SnowflakeId{id=%d, timestamp=%d (%s), worker=%d, seq=%d}",
            id, timestamp, toInstant(), workerId, sequence
        );
    }
}
