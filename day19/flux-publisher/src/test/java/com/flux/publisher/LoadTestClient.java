package com.flux.publisher;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load test client using Virtual Threads.
 * Spawns N concurrent requests to test publisher throughput.
 */
public class LoadTestClient {
    
    private static final String API_URL = "http://localhost:8080/messages";
    
    public static void main(String[] args) throws Exception {
        int totalRequests = args.length > 0 ? Integer.parseInt(args[0]) : 10_000;
        int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : 1_000;
        
        System.out.println("Load Test Configuration:");
        System.out.println("  Total Requests: " + totalRequests);
        System.out.println("  Concurrency: " + concurrency);
        System.out.println();
        
        HttpClient client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rateLimited = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        
        long startTime = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(totalRequests);
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    String json = String.format(
                        "{\"guild_id\":\"guild-%d\",\"channel_id\":\"ch-1\"," +
                        "\"user_id\":\"user-%d\",\"content\":\"Load test message %d\"}",
                        requestId % 100, requestId % 1000, requestId
                    );
                    
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                    
                    HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                    );
                    
                    switch (response.statusCode()) {
                        case 202 -> success.incrementAndGet();
                        case 429 -> rateLimited.incrementAndGet();
                        default -> errors.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("\nLoad Test Results:");
        System.out.println("  Duration: " + duration + " ms");
        System.out.println("  Throughput: " + (totalRequests * 1000L / duration) + " req/sec");
        System.out.println("  Success: " + success.get());
        System.out.println("  Rate Limited (429): " + rateLimited.get());
        System.out.println("  Errors: " + errors.get());
        
        executor.shutdown();
    }
}
