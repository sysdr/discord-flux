package com.flux.serialization.benchmark;

import com.flux.serialization.engine.*;
import com.flux.serialization.model.VoiceStateUpdate;
import com.flux.serialization.pool.BufferPool;
import com.flux.serialization.metrics.Metrics;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

public class BenchmarkRunner {
    private static final int NUM_CLIENTS = 10_000;
    private static final int MESSAGES_PER_CLIENT = 1_000;
    
    private final List<SerializationEngine> engines;
    private final BufferPool pool;
    private final ConcurrentHashMap<String, Metrics> metricsMap;
    
    public BenchmarkRunner() {
        this.engines = List.of(
            new JsonEngine(),
            new ProtobufEngine(),
            new CustomBinaryEngine()
        );
        this.pool = new BufferPool(512, false); // heap buffers for simplicity
        this.metricsMap = new ConcurrentHashMap<>();
        
        engines.forEach(e -> metricsMap.put(e.name(), new Metrics(e.name())));
    }
    
    public void runBenchmark() {
        System.out.println("🚀 Starting benchmark: " + NUM_CLIENTS + " virtual threads, " 
                         + MESSAGES_PER_CLIENT + " messages each");
        
        for (SerializationEngine engine : engines) {
            System.out.println("\n📊 Testing: " + engine.name());
            runEngineTest(engine);
            
            Metrics m = metricsMap.get(engine.name());
            System.out.printf("  ✅ Throughput: %.0f ops/s%n", m.getThroughput());
            System.out.printf("  📈 Avg Latency: %.2f µs%n", m.getAverageLatencyMicros());
            System.out.printf("  📉 P99 Latency: %d µs%n", m.getP99LatencyMicros());
        }
        
        System.out.println("\n✅ Benchmark complete! View metrics at http://localhost:8080/dashboard");
    }
    
    private void runEngineTest(SerializationEngine engine) {
        Metrics metrics = metricsMap.get(engine.name());
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch latch = new CountDownLatch(NUM_CLIENTS);
            
            for (int i = 0; i < NUM_CLIENTS; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < MESSAGES_PER_CLIENT; j++) {
                            VoiceStateUpdate msg = VoiceStateUpdate.random();
                            ByteBuffer buf = pool.acquire();
                            
                            long start = System.nanoTime();
                            
                            // Serialize
                            engine.serialize(msg, buf);
                            
                            // Prepare for reading
                            buf.flip();
                            
                            // Deserialize
                            engine.deserialize(buf);
                            
                            long elapsed = System.nanoTime() - start;
                            metrics.recordLatency(elapsed);
                            
                            pool.release(buf);
                        }
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
        }
    }
    
    public ConcurrentHashMap<String, Metrics> getMetrics() {
        return metricsMap;
    }
}
