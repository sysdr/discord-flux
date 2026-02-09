package com.flux.grpc;

import com.flux.grpc.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class LoadTestClient {
    private final ManagedChannel channel;
    private final MessageServiceGrpc.MessageServiceBlockingStub blockingStub;
    private final SnowflakeIdGenerator idGenerator;
    
    public LoadTestClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .build();
        this.blockingStub = MessageServiceGrpc.newBlockingStub(channel);
        this.idGenerator = new SnowflakeIdGenerator(1);
    }
    
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    
    public void runLoadTest(int numRequests, int concurrency) {
        System.out.println("🔥 Starting load test: " + numRequests + " requests with " + concurrency + " concurrent workers");
        
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numRequests; i++) {
            final long channelId = 123 + (i % 10);
            final long messageId = idGenerator.nextId();
            
            executor.submit(() -> {
                try {
                    InsertMessageRequest request = InsertMessageRequest.newBuilder()
                        .setChannelId(channelId)
                        .setMessageId(messageId)
                        .setAuthorId(999)
                        .setContent("Load test message " + messageId)
                        .build();
                    
                    InsertMessageResponse response = blockingStub.insertMessage(request);
                    
                    if (response.getSuccess()) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (StatusRuntimeException e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        double rps = (numRequests * 1000.0) / duration;
        
        System.out.println("\n📊 Load Test Results:");
        System.out.println("   Total Requests: " + numRequests);
        System.out.println("   Successful: " + successCount.get());
        System.out.println("   Errors: " + errorCount.get());
        System.out.println("   Duration: " + duration + "ms");
        System.out.println("   Throughput: " + String.format("%.2f", rps) + " RPS");
        
        executor.shutdown();
    }
    
    public static void main(String[] args) throws Exception {
        LoadTestClient client = new LoadTestClient("localhost", 9090);
        
        try {
            int requests = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
            int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : 100;
            
            client.runLoadTest(requests, concurrency);
        } finally {
            client.shutdown();
        }
    }
}
