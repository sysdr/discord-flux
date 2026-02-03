package com.flux.gateway;

import com.flux.typing.TypingIndicatorService;
import com.flux.metrics.Metrics;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LoadTest {
    public static void main(String[] args) {
        int numTypers = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        int durationSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  LOAD TEST: " + numTypers + " Concurrent Typers");
        System.out.println("  Duration: " + durationSeconds + " seconds");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        Metrics metrics = new Metrics();
        TypingIndicatorService service = new TypingIndicatorService(metrics);
        
        CountDownLatch latch = new CountDownLatch(numTypers);
        Random rand = new Random();
        
        long startTime = System.currentTimeMillis();
        
        // Spawn virtual threads simulating typers
        for (int i = 0; i < numTypers; i++) {
            final long userId = 10000 + i;
            final long channelId = 1001 + (i % 10); // Spread across 10 channels
            
            Thread.ofVirtual().start(() -> {
                try {
                    long endTime = startTime + (durationSeconds * 1000L);
                    
                    while (System.currentTimeMillis() < endTime) {
                        service.handleTypingEvent(userId, channelId);
                        
                        // Simulate typing bursts
                        Thread.sleep(rand.nextInt(2000) + 1000); // 1-3 sec between events
                    }
                } catch (InterruptedException e) {
                    // Normal shutdown
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for completion
        try {
            latch.await(durationSeconds + 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  RESULTS");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  Published:  " + metrics.getPublished());
        System.out.println("  Throttled:  " + metrics.getThrottled());
        System.out.println("  Dropped:    " + metrics.getDropped());
        System.out.println("  Elapsed:    " + (elapsed / 1000.0) + " seconds");
        System.out.println("  Rate:       " + (metrics.getPublished() * 1000L / elapsed) + " events/sec");
        System.out.println("  Saturation: " + String.format("%.2f%%", service.getRing().getSaturation() * 100));
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
