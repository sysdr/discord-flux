# Flux Typing Indicators

Production-grade typing indicator implementation using lock-free ring buffers and zero-copy serialization.

## Quick Start

```bash
# 1. Start the Gateway + Dashboard
./start.sh

# 2. Open browser to http://localhost:8080

# 3. Click "Simulate 50 Typers" to see real-time updates

# 4. Run load test
./demo.sh 1000  # 1000 concurrent typers

# 5. Verify everything works
./verify.sh

# 6. Cleanup
./cleanup.sh
```

## Architecture Highlights

- **Lock-Free Ring Buffer**: 16K entry circular buffer using `MemorySegment`
- **VarHandle Atomics**: CAS-based throttling without `AtomicLong` allocation
- **Zero-Copy Serialization**: Reusable byte templates with inline ID swapping
- **Virtual Threads**: Thousands of simulated clients with minimal overhead
- **TTL-Based Eviction**: No background threads, lazy expiration on read

## Requirements

- Java 21+
- Maven 3.9+

## Metrics

- **Published**: Total typing events accepted
- **Throttled**: Events rejected due to rate limiting (1 per user per 3 sec)
- **Dropped**: Events dropped due to full buffers (backpressure)
- **Saturation**: Ring buffer fill percentage

## Testing

```bash
# Unit tests
mvn test

# Load test with 500 typers for 30 seconds
./demo.sh 500 30
```

## Homework

Implement adaptive ring sizing to handle extreme load spikes without dropping events.
