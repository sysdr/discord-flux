package com.flux;

import com.flux.model.Message;
import com.flux.service.MessageService;
import com.flux.service.MetricsCollector;
import com.flux.service.ScyllaConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTest {
    private static final Logger logger = LoggerFactory.getLogger(LoadTest.class);

    public static void main(String[] args) throws Exception {
        int messageCount = args.length > 0 ? Integer.parseInt(args[0]) : 10000;
        int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        
        logger.info("Starting load test: {} messages, {} concurrent threads", 
            messageCount, concurrency);
        
        try (ScyllaConnection connection = new ScyllaConnection("127.0.0.1", 9042, "datacenter1")) {
            MessageService service = new MessageService(connection);
            MetricsCollector metrics = new MetricsCollector();
            
            long startTime = System.nanoTime();
            CountDownLatch latch = new CountDownLatch(messageCount);
            AtomicInteger completed = new AtomicInteger(0);
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < messageCount; i++) {
                    final int messageNum = i;
                    
                    executor.submit(() -> {
                        try {
                            long channelId = (messageNum % 100) + 1; // 100 channels
                            long userId = ThreadLocalRandom.current().nextLong(1, 10000);
                            String content = "Test message " + messageNum;
                            
                            Message message = Message.create(channelId, userId, content);
                            
                            long writeStart = System.nanoTime();
                            service.insertMessage(message);
                            long writeEnd = System.nanoTime();
                            
                            metrics.recordWrite(writeEnd - writeStart);
                            
                            int count = completed.incrementAndGet();
                            if (count % 1000 == 0) {
                                logger.info("Progress: {}/{} messages", count, messageCount);
                            }
                            
                        } catch (Exception e) {
                            metrics.recordError();
                            logger.error("Write failed", e);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                
                latch.await();
            }
            
            long endTime = System.nanoTime();
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            
            logger.info("=".repeat(60));
            logger.info("Load Test Results:");
            logger.info("Total Messages: {}", messageCount);
            logger.info("Total Errors: {}", metrics.getTotalErrors());
            logger.info("Duration: {} seconds", String.format("%.2f", durationSeconds));
            logger.info("Throughput: {} writes/sec", String.format("%.2f", messageCount / durationSeconds));
            logger.info("P99 Latency: {} μs", metrics.getWriteLatency().getP99());
            logger.info("=".repeat(60));
        }
    }
}
