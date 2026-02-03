package com.flux.benchmark;

import com.flux.core.Message;
import com.flux.core.SnowflakeGenerator;
import com.flux.persistence.LSMSimulator;
import com.flux.persistence.PostgresWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark harness comparing Postgres vs LSM writes.
 */
public class BenchmarkRunner {
    
    private static final int BATCH_SIZE = 1000;
    private static final int VIRTUAL_USERS = 1000;
    private static final int MESSAGES_PER_USER = 100;
    
    private final SnowflakeGenerator idGen = new SnowflakeGenerator(1, 1);
    private final Random random = new Random();
    
    public BenchmarkResult runPostgresBenchmark(PostgresWriter writer) {
        System.out.println("Starting Postgres benchmark...");
        writer.reset();
        
        var startTime = System.nanoTime();
        var errors = new AtomicLong(0);
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < VIRTUAL_USERS; i++) {
                final int userId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < MESSAGES_PER_USER / BATCH_SIZE; j++) {
                            var batch = generateBatch();
                            writer.writeBatch(batch);
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                });
            }
        }
        
        var endTime = System.nanoTime();
        var durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        var throughput = (writer.getWrittenCount() * 1000.0) / durationMs;
        
        return new BenchmarkResult(
            "Postgres",
            writer.getWrittenCount(),
            errors.get(),
            durationMs,
            throughput
        );
    }
    
    public BenchmarkResult runLSMBenchmark(LSMSimulator simulator) {
        System.out.println("Starting LSM benchmark...");
        simulator.reset();
        
        var startTime = System.nanoTime();
        var written = new AtomicLong(0);
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < VIRTUAL_USERS; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < MESSAGES_PER_USER; j++) {
                        var msg = generateMessage();
                        simulator.append(msg);
                        written.incrementAndGet();
                    }
                });
            }
        }
        
        var endTime = System.nanoTime();
        var durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        var throughput = (written.get() * 1000.0) / durationMs;
        
        return new BenchmarkResult(
            "LSM Simulation",
            written.get(),
            0,
            durationMs,
            throughput
        );
    }
    
    private List<Message> generateBatch() {
        var batch = new ArrayList<Message>(BATCH_SIZE);
        for (int i = 0; i < BATCH_SIZE; i++) {
            batch.add(generateMessage());
        }
        return batch;
    }
    
    private Message generateMessage() {
        var channelId = "channel_" + random.nextInt(1000);
        var content = generateContent();
        return new Message(idGen.nextId(), channelId, content);
    }
    
    private String generateContent() {
        var words = new String[]{"Hello", "Discord", "Chat", "Message", "Test", "Benchmark", "Performance"};
        var sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(words[random.nextInt(words.length)]).append(" ");
        }
        return sb.toString().trim();
    }
    
    public record BenchmarkResult(
        String name,
        long messagesWritten,
        long errors,
        long durationMs,
        double throughputPerSec
    ) {
        @Override
        public String toString() {
            return String.format("""
                %s Results:
                  Messages Written: %,d
                  Errors: %d
                  Duration: %,d ms
                  Throughput: %,.0f msg/sec
                """, name, messagesWritten, errors, durationMs, throughputPerSec);
        }
    }
}
