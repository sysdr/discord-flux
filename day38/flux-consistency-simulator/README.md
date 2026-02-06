# Flux Consistency Simulator

A production-grade implementation of Cassandra's tunable consistency model in pure Java 21.

## What This Teaches

- How Cassandra's coordinator pattern works under the hood
- The latency/availability tradeoff between ONE and QUORUM
- Why Discord uses ONE for message writes (speed) and QUORUM for critical metadata
- How network partitions break QUORUM availability

## Quick Start
```bash
./start.sh
# Open http://localhost:8080
```

## Run Load Test
```bash
./demo.sh
```

## Verify Consistency Behavior
```bash
./verify.sh
```

## Architecture

- 3 ReplicaNode instances (in-memory storage)
- 1 CoordinatorNode (routes requests)
- Virtual Threads for replica operations
- Real network latency simulation (5-20ms)

## Key Files

- `SimulatorServer.java` - HTTP server + dashboard
- `CoordinatorNode.java` - Implements consistency levels
- `ReplicaNode.java` - Simulated storage node
- `SnowflakeGenerator.java` - Time-sortable IDs
