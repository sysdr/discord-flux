package com.flux.persistence;

import org.junit.jupiter.api.Test;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class MessagePartitionTest {
    @Test void testHourlyBucket() {
        var zdt = ZonedDateTime.of(2025, 2, 4, 14, 30, 45, 0, ZoneOffset.UTC);
        assertEquals(2025020414, MessagePartition.hourlyBucket(zdt.toInstant().toEpochMilli()));
    }
    @Test void testDecrementBucket() {
        assertEquals(2025020413, MessagePartition.decrementBucket(2025020414));
        assertEquals(2025020323, MessagePartition.decrementBucket(2025020400));
        assertEquals(2025013023, MessagePartition.decrementBucket(2025020100));
    }
    @Test void testPartitionRecordImmutability() {
        var p = new MessagePartition(12345L, 2025020414);
        assertEquals(12345L, p.channelId());
        assertEquals(2025020414, p.bucket());
    }
}
