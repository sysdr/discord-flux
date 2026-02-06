package com.flux.persistence;

import java.time.Instant;
import java.time.ZoneOffset;

public record MessagePartition(long channelId, int bucket) {
    public static int hourlyBucket(long epochMilli) {
        var instant = Instant.ofEpochMilli(epochMilli);
        var zdt = instant.atZone(ZoneOffset.UTC);
        return zdt.getYear() * 1_000_000 +
               zdt.getMonthValue() * 10_000 +
               zdt.getDayOfMonth() * 100 +
               zdt.getHour();
    }

    public static int decrementBucket(int bucket) {
        int year = bucket / 1_000_000;
        int month = (bucket / 10_000) % 100;
        int day = (bucket / 100) % 100;
        int hour = bucket % 100;
        if (hour > 0) return bucket - 1;
        if (day > 1) return year * 1_000_000 + month * 10_000 + (day - 1) * 100 + 23;
        if (month > 1) return year * 1_000_000 + (month - 1) * 10_000 + 30 * 100 + 23;
        return (year - 1) * 1_000_000 + 12 * 10_000 + 31 * 100 + 23;
    }
}
