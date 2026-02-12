# Flux Migration - Day 45: Zero-Downtime Migration from JSON to ScyllaDB

## Quick Start

### Prerequisites
1. **Java 21+** - `java --version`
2. **Maven 3.9+** - `mvn --version`
3. **ScyllaDB/Cassandra** - Run via Docker:
   ```bash
   docker run -d --name scylla -p 9042:9042 scylladb/scylla
   ```

### Run the Demo
```bash
bash demo.sh
```

### Start the Dashboard
```bash
bash start.sh
# Open http://localhost:8080/dashboard
```

### Verify Migration
```bash
bash verify.sh
```

### Cleanup
```bash
bash cleanup.sh
```

## Architecture

- **StreamingJsonParser**: Zero-copy JSON parsing with NIO
- **CassandraWriter**: Virtual Thread-based writer with backpressure
- **CheckpointManager**: Crash-safe progress tracking
- **MigrationOrchestrator**: Coordinates the pipeline

## Key Metrics to Watch

1. **Heap Usage**: Should stay <4GB regardless of dataset size
2. **Virtual Threads**: Should stabilize at semaphore limit (1000)
3. **GC Rate**: Target <500MB/sec allocation
4. **Cassandra p99**: Keep <50ms write latency

## Homework

1. Implement object pooling for BoundStatements
2. Add dark read verification
3. Build adaptive rate limiter based on Cassandra latency
4. Scale to multi-node parallel migration
