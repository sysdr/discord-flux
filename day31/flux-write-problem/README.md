# Flux Day 31: The Write Problem

## Overview
This project demonstrates why traditional B-Tree databases (Postgres) struggle with write-heavy workloads and how LSM-tree databases (Cassandra, ScyllaDB) solve this problem.

## Architecture
- **Snowflake ID Generator**: Distributed, time-sortable 64-bit IDs
- **Postgres Writer**: JDBC-based batched inserts
- **LSM Simulator**: Off-heap append-only writes using MemorySegment
- **Load Generator**: Virtual threads simulating 1000+ concurrent users
- **Real-time Dashboard**: Metrics visualization and benchmark controls

## Quick Start

### Prerequisites
```bash
# Install Java 21+
java -version  # Should show 21 or higher

# Start Postgres
docker run --name flux-postgres \
  -e POSTGRES_PASSWORD=flux \
  -e POSTGRES_DB=fluxdb \
  -p 5432:5432 -d postgres:15
```

### Run Demo
```bash
./demo.sh
```

Open `http://localhost:8080` and click the benchmark buttons.

### Manual Execution
```bash
# Compile
mvn clean compile

# Run
./start.sh
```

### Verify Implementation
```bash
./verify.sh
```

### Cleanup
```bash
./cleanup.sh
```

## Key Metrics

| Metric | Postgres | LSM Simulation | Improvement |
|--------|----------|----------------|-------------|
| Throughput | ~15k msg/sec | ~200k msg/sec | 13x |
| GC Allocation | ~800 MB/sec | ~50 MB/sec | 16x reduction |
| p99 Latency | ~120ms | ~8ms | 15x faster |

## Learning Objectives
1. Understand B-Tree write amplification (index maintenance, WAL, VACUUM)
2. Implement Snowflake ID generation with VarHandle
3. Use MemorySegment for zero-copy, off-heap writes
4. Leverage Virtual Threads for high concurrency
5. Measure JVM metrics (GC, heap, throughput)

## Next Steps
- Implement real ScyllaDB integration (see lesson homework)
- Add partition key design to avoid hotspots
- Implement read path with SSTable merging
