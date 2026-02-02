# Flux Day 27: Presence System II - Broadcasting State Changes

## Overview

This project demonstrates **zero-allocation presence broadcasting** for a Discord-style gateway handling thousands of concurrent users in a guild. It showcases:

- **Lock-free guild member registry** using `ConcurrentHashMap`
- **Virtual Thread fan-out** to avoid thread pool exhaustion
- **Per-connection ring buffers** for backpressure isolation
- **Presence coalescing** to reduce redundant broadcasts
- **Real-time metrics dashboard**

## The N-Squared Problem

When a user's presence changes in a 5,000-member guild, we must notify 4,999 other users. If 100 users change presence simultaneously, that's 500,000 individual message sends. This project solves:

- **Heap explosion** from per-recipient object allocation
- **Thread starvation** from blocking I/O
- **Lock contention** from synchronized member lists
- **Backpressure cascades** from slow consumers

## Quick Start

```bash
# Generate project
bash setup.sh
cd flux-presence-broadcast

# Run demo (1,000 members, 20 updates/sec, 60 seconds)
bash demo.sh

# Open dashboard
open http://localhost:8080/dashboard

# Run verification
bash verify.sh

# Clean up
bash cleanup.sh
```

## Architecture

```
PresenceUpdate (immutable record)
    ↓
PresenceBroadcaster
    ↓ (Virtual Thread per recipient)
ConnectionRingBuffer (per connection)
    ↓
GatewayConnection
```

### Key Components

1. **PresenceUpdate**: Immutable record shared across all recipients
2. **GuildMemberRegistry**: Lock-free member tracking with `CopyOnWriteArrayList`
3. **PresenceBroadcaster**: Virtual Thread-based fan-out engine
4. **ConnectionRingBuffer**: Lock-free MPSC queue with overflow protection
5. **GatewayConnection**: Individual client connection with ring buffer

## Load Testing

```bash
# Simulate 10,000 users, 100 updates/sec, 120 seconds
bash load-test.sh 10000 100 120
```

Expected output:
```
[5s] Broadcasts: 450 (90.0/s) | Messages: 4,498,550 (899,710/s) | Dropped: 123 | Slow: 5
[10s] Broadcasts: 920 (92.0/s) | Messages: 9,197,080 (919,708/s) | Dropped: 456 | Slow: 12
```

## Metrics to Monitor

### JVM Heap
```bash
jstat -gc <PID> 1000 10
```
Target: Young Gen allocation rate < 1 MB/sec

### Virtual Threads
```bash
jcmd <PID> Thread.print | grep VirtualThread | wc -l
```
Should see 1,000+ Virtual Threads during active broadcasts

### GC Pauses
```bash
jcmd <PID> GC.heap_info
```
Target: Pause times < 50ms

## Performance Goals

- **Latency**: P99 broadcast latency < 50ms
- **Throughput**: 1 million messages/sec with 10K-member guild
- **Memory**: Heap allocation rate < 1 MB/sec
- **GC**: Young Gen pauses < 50ms
- **Drop Rate**: < 0.01% message drops

## Implementation Highlights

### Zero-Allocation Serialization
```java
private static final ThreadLocal<ByteBuffer> BUFFER_POOL = 
    ThreadLocal.withInitial(() -> ByteBuffer.allocateDirect(512));

ByteBuffer shared = serializeOnce(update);
for (ConnectionHandle conn : recipients) {
    conn.ringBuffer.offer(shared.asReadOnlyBuffer()); // Share read-only view
}
```

### Lock-Free Ring Buffer
```java
do {
    current = (int) WRITE_INDEX.getVolatile(this);
    next = (current + 1) & mask;
} while (!WRITE_INDEX.compareAndSet(this, current, next));

ring[next] = message;
```

### Virtual Thread Fan-Out
```java
ExecutorService fanOutExecutor = Executors.newVirtualThreadPerTaskExecutor();

for (ConnectionHandle conn : recipients) {
    fanOutExecutor.submit(() -> conn.ringBuffer.offer(message));
}
```

## Troubleshooting

**High heap allocation rate:**
- Check you're using `ByteBuffer.asReadOnlyBuffer()` to share serialized messages
- Verify no intermediate `String` or JSON objects created per-recipient

**GC pauses > 50ms:**
- Reduce message coalescing window from 200ms to 100ms
- Increase ring buffer size to reduce drop-triggered allocations

**Slow consumer warnings:**
- Network issues on client side
- Increase ring buffer capacity from 1024 to 4096
- Consider disconnecting clients with >1% drop rate

## License

MIT License - Flux Course Material
