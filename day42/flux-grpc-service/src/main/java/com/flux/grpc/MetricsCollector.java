package com.flux.grpc;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MetricsCollector {
    private static final ConcurrentHashMap<String, LongAdder> requestCounts = new ConcurrentHashMap<>();
    private static final AtomicLong totalRequests = new AtomicLong(0);
    private static final AtomicLong totalStreamedMessages = new AtomicLong(0);
    private static final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    
    public static void recordRequest(String method) {
        requestCounts.computeIfAbsent(method, k -> new LongAdder()).increment();
        totalRequests.incrementAndGet();
    }
    
    public static void recordStreamedMessages(int count) {
        totalStreamedMessages.addAndGet(count);
    }
    
    public static long getRequestCount(String method) {
        LongAdder adder = requestCounts.get(method);
        return adder != null ? adder.sum() : 0;
    }
    
    public static long getTotalRequests() {
        return totalRequests.get();
    }
    
    public static long getTotalStreamedMessages() {
        return totalStreamedMessages.get();
    }
    
    public static long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime.get()) / 1000;
    }
    
    public static String getMetricsJson() {
        return String.format("""
            {
              "total_requests": %d,
              "insert_message": %d,
              "get_message": %d,
              "stream_history": %d,
              "delete_message": %d,
              "streamed_messages": %d,
              "uptime_seconds": %d,
              "db_connected": %s
            }
            """,
            getTotalRequests(),
            getRequestCount("InsertMessage"),
            getRequestCount("GetMessage"),
            getRequestCount("StreamMessageHistory"),
            getRequestCount("DeleteMessage"),
            getTotalStreamedMessages(),
            getUptimeSeconds(),
            ScyllaDBClient.isConnected()
        );
    }
}
