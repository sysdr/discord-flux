# Flux Wide-Column Basics - ScyllaDB Setup

## Quick Start

1. **Start ScyllaDB**:
   ```bash
   docker compose up -d
   ```

2. **Wait for database** (check logs):
   ```bash
   docker logs scylla -f
   # Wait for "Starting listening for CQL clients"
   ```

3. **Start the application**:
   ```bash
   ./start.sh
   ```

4. **Open dashboard**:
   http://localhost:8080/dashboard

5. **Run demo scenario**:
   ```bash
   ./demo.sh
   ```

6. **Verify results**:
   ```bash
   ./verify.sh
   ```

## Project Structure

```
flux-day32-scylladb/
├── src/main/java/com/flux/
│   ├── model/              # Data models (Message, ChannelStats)
│   ├── service/            # Business logic (MessageService, ScyllaConnection)
│   ├── dashboard/          # HTTP dashboard server
│   └── FluxApplication.java
├── src/test/java/com/flux/
│   └── LoadTest.java       # Load testing harness
├── docker-compose.yml      # ScyllaDB container
├── start.sh                # Compile and run
├── demo.sh                 # Run demo scenario
├── verify.sh               # Verify database state
└── cleanup.sh              # Clean environment
```

## Key Learnings

1. **Partition Keys**: Data distribution across nodes
2. **Clustering Keys**: Physical sorting within partitions
3. **Prepared Statements**: Parse once, execute many times
4. **Async Execution**: Non-blocking I/O with Virtual Threads
5. **LSM Tree Architecture**: Write-optimized storage engine

## Homework

Optimize `MessageService.insertMessage()` to batch inserts by channel_id.
Measure throughput improvement with `./demo.sh`.
