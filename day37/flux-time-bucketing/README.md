# Flux Day 37: Time Bucketing

Production implementation of time-bucketed partition keys for Cassandra/ScyllaDB.

## Quick Start

```bash
# Start the application
./start.sh

# Open dashboard
open http://localhost:8080

# Run comparison demo
./demo.sh

# Verify correctness
./verify.sh

# Cleanup
./cleanup.sh
```

## What You'll Learn

- Why unbounded partitions kill Cassandra performance
- How to implement deterministic time bucketing using integer math
- The tradeoff between query complexity and write distribution
- How to calculate optimal bucket size based on write velocity

## Architecture

```
Message (timestamp) 
    ↓
PartitionKeyGenerator.calculateBucket()
    ↓
Bucket ID (0, 1, 2...)
    ↓
Cassandra Partition Key: (user_id, bucket_id)
```

## Key Files

- `PartitionKeyGenerator.java` - Core bucketing algorithm
- `MessageSimulator.java` - Load generator with Zipfian distribution
- `DashboardServer.java` - Real-time visualization
- `PartitionKeyGeneratorTest.java` - Comprehensive test suite
