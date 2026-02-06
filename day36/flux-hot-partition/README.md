# Flux Day 36: The Hot Partition Problem

## Overview
This project demonstrates why naive partitioning strategies fail at scale in distributed wide-column stores like Cassandra/ScyllaDB, and how time-bucketed partitioning solves the problem.

## Quick Start

```bash
# 1. Build and start the dashboard
./scripts/start.sh

# 2. Open browser to http://localhost:8080

# 3. Run demo scenarios (in another terminal)
./scripts/demo.sh

# 4. Verify implementation
./scripts/verify.sh

# 5. Cleanup
./scripts/cleanup.sh
```

## Key Concepts

### The Problem: Hot Partitions
When all messages for a channel are stored in a single partition:
- **Memory pressure** during reads (gigabytes of data scanned)
- **Compaction storms** (hours to compact multi-GB partitions)
- **Coordinator overload** (one node handles all traffic)
- **Unbounded growth** (partition size grows indefinitely)

### The Solution: Time-Bucketed Partitioning
Split data across multiple partitions using time windows:
```
Naive:    (channel_id) → All messages
Bucketed: (channel_id, time_bucket) → Messages per hour/day
```

## Project Structure
```
src/main/java/com/flux/
├── generator/
│   └── SnowflakeGenerator.java    # Distributed ID generation
├── partition/
│   ├── BucketStrategy.java        # Time bucketing strategies
│   ├── Message.java               # Message record
│   └── PartitionKey.java          # Partition key logic
├── simulator/
│   └── PartitionSimulator.java    # Write workload simulation
└── server/
    └── DashboardServer.java       # Real-time visualization
```

## Implementation Highlights

### Lock-Free ID Generation (VarHandle)
```java
private static final VarHandle SEQUENCE;
public long nextId() {
    // CAS loop for thread-safe sequence increment
    while (true) {
        if (SEQUENCE.compareAndSet(this, current, next)) {
            break;
        }
    }
}
```

### Virtual Threads for Concurrency
```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < totalMessages; i++) {
        executor.submit(() -> {
            // Each write is a lightweight virtual thread
        });
    }
}
```

## Monitoring Metrics

### What to Watch
1. **Partition Count** - Should grow over time with bucketing
2. **Max Partition Size** - Should stay under 100MB (or 100K messages)
3. **Write Distribution** - Should be evenly spread across partitions
4. **Memory Usage** - Heap should remain stable despite write volume

### VisualVM Checks
- Heap: Stable around 200-300MB
- GC: Minor GC every 5-10 seconds (normal)
- Threads: < 50 platform threads (Virtual Threads don't create OS threads)

## Homework Challenge
Optimize the Snowflake generator using thread-local sequence counters to reduce contention. Measure the throughput improvement using JFR.

## Next Lesson
Day 37: Implementing Cassandra client with DataStax driver, connection pooling, and retry policies.
