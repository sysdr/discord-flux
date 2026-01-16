# Flux Day 7: Concurrency Models

Comparison of three concurrency models for handling massive concurrent connections:
- **Thread-per-Connection**: Traditional OS threads (baseline)
- **NIO Reactor**: Single-threaded event loop with Selector
- **Virtual Threads**: Java 21's lightweight threading

## Quick Start

```bash
# Verify setup
./verify.sh

# Start interactive server
./start.sh

# Run automated demo
./demo.sh nio 10000 5

# View dashboard
open http://localhost:8080
```

## Project Structure

```
src/main/java/com/flux/gateway/concurrency/
├── threadper/          # Thread-per-connection implementation
├── nioreactor/         # NIO Reactor implementation
├── virtual/            # Virtual Threads implementation
├── common/             # Shared interfaces and metrics
├── dashboard/          # Real-time monitoring dashboard
├── metrics/            # Load testing client
└── Main.java           # Application entry point
```

## Key Learning Points

1. **Thread-per-Connection crashes at ~10k connections** due to memory exhaustion
2. **NIO Reactor handles 100k+ connections** with a single thread but complex state management
3. **Virtual Threads provide simple code + massive scale** by multiplexing onto carrier threads

## Monitoring

The dashboard (`http://localhost:8080`) shows real-time:
- Active connections
- Throughput (messages/sec)
- Memory usage
- Total bytes transferred

Use `jconsole` or VisualVM to monitor:
- Thread count (Thread-per-Connection vs Virtual Threads)
- GC activity
- Memory allocation rate

## Cleanup

```bash
./cleanup.sh
```
