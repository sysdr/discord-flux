# Flux Worker Pool - Day 8 Exercise

## Quick Start

1. **Generate & Start:**
```bash
   ./start.sh
```

2. **Open Dashboard:**
   http://localhost:9090

3. **Run Load Test:**
```bash
   ./demo.sh
```

4. **Verify Health:**
```bash
   ./verify.sh
```

5. **Cleanup:**
```bash
   ./cleanup.sh
```

## What You'll Learn

- Separating I/O from CPU-bound processing
- Virtual Threads for scalable concurrency
- Bounded queues for backpressure
- Zero-copy buffer handling
- Production metrics (p50/p99 latency, rejection rate)

## Files

- `GatewayServer.java` - NIO selector + task enqueuing
- `WorkerPool.java` - Virtual Thread pool implementation
- `Task.java` - Immutable work unit (Java Record)
- `Dashboard.java` - Real-time monitoring UI
- `LoadTest.java` - 1000 concurrent clients

## Next Steps

See `lesson_article.md` for the full engineering deep-dive.
