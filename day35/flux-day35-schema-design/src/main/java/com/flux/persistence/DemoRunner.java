package com.flux.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DemoRunner {
    public static void main(String[] args) throws Exception {
        System.out.println("Running Demo: 24-Hour Write Storm Simulation");
        try (var session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
                .withLocalDatacenter("datacenter1")
                .withKeyspace("flux")
                .build()) {
            var writer = new MessageWriter(session);
            var reader = new MessageReader(session);
            var random = new Random();
            long channelId = 12345L;
            int messagesPerHour = 1000;
            int hoursToSimulate = 24;
            System.out.printf("Writing %d messages across %d hourly buckets...%n", messagesPerHour * hoursToSimulate, hoursToSimulate);
            var startTime = Instant.now().minus(24, ChronoUnit.HOURS);
            var allWrites = new ArrayList<CompletableFuture<Void>>();
            for (int hour = 0; hour < hoursToSimulate; hour++) {
                var hourStart = startTime.plus(hour, ChronoUnit.HOURS);
                for (int i = 0; i < messagesPerHour; i++) {
                    var timestamp = hourStart.plus(random.nextInt(3600), ChronoUnit.SECONDS).toEpochMilli();
                    var message = new Message(channelId, UUID.randomUUID(), random.nextLong(1000, 9999),
                        "Message content " + i + " at hour " + hour, timestamp);
                    allWrites.add(writer.writeMessage(message));
                }
                if ((hour + 1) % 6 == 0) System.out.printf("  Completed %d/%d hours%n", hour + 1, hoursToSimulate);
            }
            CompletableFuture.allOf(allWrites.toArray(new CompletableFuture[0])).join();
            System.out.printf("Wrote %d messages total%n", writer.getWriteCount());
            System.out.println("Fetching latest 50 messages...");
            var latest = reader.fetchLatestMessages(channelId, 50);
            System.out.printf("Retrieved %d messages%n", latest.size());
            if (!latest.isEmpty()) {
                var first = latest.get(0);
                System.out.printf("   Latest: %s (bucket: %d)%n", first.content(), MessagePartition.hourlyBucket(first.timestamp()));
            }
            System.out.println("Demo complete! Check dashboard: http://localhost:8080");
        }
    }
}
