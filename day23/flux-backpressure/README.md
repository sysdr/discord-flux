# Flux Day 23: Backpressure Detection

## Overview

This project demonstrates production-grade TCP backpressure detection in a Java NIO gateway server. When broadcasting messages to thousands of connections, slow consumers (clients with filled TCP send buffers) are detected and evicted to prevent memory exhaustion.

## Architecture

- **Non-blocking NIO:** Uses `java.nio.channels.Selector` for event-driven I/O
- **Per-connection ring buffers:** Fixed-size bounded queues (256 slots × 1KB)
- **Lock-free design:** VarHandle atomics for zero-contention operations
- **Backpressure detection:** Monitors `SelectionKey.OP_WRITE` events
- **Slow consumer eviction:** Automatic disconnect after 5 seconds in backpressure state

## Quick Start

1. **Start the gateway:**
   ```bash
   ./start.sh
   ```

2. **Open the dashboard:**
   Navigate to http://localhost:8080

3. **Run the demo:**
   ```bash
   ./demo.sh
   ```

4. **Verify operation:**
   ```bash
   ./verify.sh
   ```

## Project Structure

```
flux-backpressure/
├── src/main/java/com/flux/backpressure/
│   ├── GatewayServer.java      # Main NIO event loop
│   ├── Connection.java         # Per-client connection state
│   ├── RingBuffer.java         # Lock-free message queue
│   ├── BackpressureMetrics.java # Metrics tracking
│   ├── Dashboard.java          # Real-time web UI
│   └── LoadTestClient.java     # Load generator
├── src/test/java/
│   └── BackpressureTest.java   # JUnit tests
├── start.sh                     # Launch server + dashboard
├── demo.sh                      # Run full demonstration
├── verify.sh                    # Automated verification
└── cleanup.sh                   # Stop processes, clean artifacts
```

## Key Metrics

- **Backpressure Events:** Number of times a connection's ring buffer filled
- **Slow Consumer Evictions:** Connections disconnected due to sustained backpressure
- **Write Success Rate:** Percentage of immediate write successes (target: >95%)
- **Buffer Utilization:** Per-connection queue depth (color-coded in dashboard)

## Performance Characteristics

- **Zero-allocation hot path:** No object creation during broadcasts to fast consumers
- **Bounded memory:** Total buffer memory = num_connections × 256KB
- **Low latency:** <5µs per broadcast operation (fast path)
- **Scalable:** Handles 100k+ connections on modern hardware

## Homework

Implement a ByteBuffer pool to eliminate allocation overhead and reduce GC pressure. Target: <1 MB/sec allocation rate under sustained load.

See `lesson_23_article.md` for detailed implementation guide.
