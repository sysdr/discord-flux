# Flux Day 9: Netpoll Optimization (Reactor Pattern)

## Quick Start
```bash
# Start the reactor server + dashboard
bash start.sh

# In another terminal: Run load test
bash demo.sh

# Verify performance metrics
bash verify.sh

# Cleanup
bash cleanup.sh
```

## Architecture

This project implements a production-grade Reactor pattern using Java NIO:
- Single Selector thread handles 100k+ connections via epoll/kqueue
- Virtual Threads (Java 21) process business logic without blocking I/O
- ByteBuffer pooling eliminates allocation in the hot path
- Real-time dashboard visualizes connection state

## Monitoring

- **Dashboard**: http://localhost:8080/dashboard
- **VisualVM**: Attach to `ReactorMain` process to inspect threads/heap
- **JFR**: Run `jcmd <pid> JFR.start` for detailed profiling

## Testing
```bash
# Unit tests
mvn test

# Load test (10k connections)
mvn exec:java -Dexec.mainClass="com.flux.netpoll.LoadTestClient" -Dexec.args="10000"
```

## Expected Metrics

- **Heap Usage**: Flat (no growth over time)
- **Thread Count**: <20 (regardless of connection count)
- **GC Allocation Rate**: <500MB/sec at 50k connections
- **Selector Wake Rate**: 10-100ms intervals
