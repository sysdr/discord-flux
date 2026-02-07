# Flux Day 40: Tombstone Deletion System

## Overview
This project demonstrates why distributed, LSM-based storage systems (like Cassandra/ScyllaDB) use tombstones instead of immediate deletes.

## Quick Start
```bash
# 1. Start the server
./start.sh

# 2. Open dashboard
open http://localhost:8080

# 3. Run automated demo
./demo.sh

# 4. Verify functionality
./verify.sh

# 5. Run tests
mvn test

# 6. Cleanup
./cleanup.sh
```

## Key Concepts

- **Tombstones**: Deletion markers in append-only storage
- **LSM Trees**: Log-Structured Merge Trees for write-heavy workloads
- **Compaction**: Background process to merge SSTables and remove tombstones
- **Read Amplification**: Cost of scanning tombstones during reads

## Architecture

- `MessageStore`: Core LSM simulation with MemTable + SSTables
- `Tombstone`: Deletion marker record
- `Compaction`: Periodic merge and cleanup
- `Dashboard`: Real-time visualization

## Monitoring

Use VisualVM to observe:
- Heap usage during compaction
- Virtual thread count (should stay low)
- GC activity (should be minimal)
```bash
jvisualvm &
# Attach to FluxTombstoneServer process
```
