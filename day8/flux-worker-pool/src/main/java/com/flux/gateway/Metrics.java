package com.flux.gateway;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight metrics system using atomic counters.
 * In production, integrate with Prometheus/Micrometer.
 */
public class Metrics {
    private static final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public static void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    public static void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong()).set(value);
    }

    public static long getCounter(String name) {
        LongAdder adder = counters.get(name);
        return adder != null ? adder.sum() : 0;
    }

    public static long getGauge(String name) {
        AtomicLong gauge = gauges.get(name);
        return gauge != null ? gauge.get() : 0;
    }

    public static String snapshot() {
        StringBuilder sb = new StringBuilder("=== Metrics Snapshot ===\n");
        counters.forEach((k, v) -> sb.append(k).append(": ").append(v.sum()).append("\n"));
        gauges.forEach((k, v) -> sb.append(k).append(": ").append(v.get()).append("\n"));
        return sb.toString();
    }
}
