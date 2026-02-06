package com.flux.partition;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Strategies for bucketing messages into partitions by time.
 */
public enum BucketStrategy {
    NAIVE {
        @Override
        public String computeBucket(long timestamp) {
            return "ALL";
        }
    },
    HOURLY {
        @Override
        public String computeBucket(long timestamp) {
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        }
    },
    DAILY {
        @Override
        public String computeBucket(long timestamp) {
            return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
    },
    WEEKLY {
        @Override
        public String computeBucket(long timestamp) {
            ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC);
            int weekOfYear = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            return "%d-W%02d".formatted(date.getYear(), weekOfYear);
        }
    };

    public abstract String computeBucket(long timestamp);
}
