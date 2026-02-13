# Flux Service Discovery

Production-grade service discovery implementation for Gateway clusters using Redis and Java 21 Virtual Threads.

## Architecture

- **Lease-based Registration**: Atomic TTL-based node registration
- **Virtual Thread Heartbeats**: No OS thread exhaustion
- **Redis Pub/Sub Events**: Instant node join/leave notifications
- **Zero-Copy Metadata**: Off-heap ByteBuffers for large datasets
- **Non-blocking Discovery**: SCAN-based queries with pipelining

## Prerequisites

- Java 21+ (JDK 21.0.1 or later)
- Maven 3.9+
- Redis 7.0+ (Docker: `docker run -p 6379:6379 redis:7-alpine`)

## Quick Start

```bash
# Start Redis
docker run -d -p 6379:6379 --name flux-redis redis:7-alpine

# Launch application
bash start.sh

# Open dashboard
open http://localhost:8080

# Run demo scenarios
bash demo.sh

# Verify health
bash verify.sh

# Cleanup
bash cleanup.sh
```

## Project Structure

```
flux-service-discovery/
├── src/main/java/com/flux/discovery/
│   ├── ServiceNode.java          # Immutable node representation
│   ├── ServiceRegistry.java      # Core registry with Redis
│   ├── GatewaySimulator.java     # Node simulator
│   ├── Dashboard.java            # HTTP dashboard
│   └── FluxApplication.java      # Main entry point
├── src/test/java/com/flux/discovery/
│   └── ServiceRegistryTest.java  # Integration tests
├── start.sh                      # Start application
├── demo.sh                       # Run demo scenarios
├── verify.sh                     # Verify health
└── cleanup.sh                    # Cleanup resources
```

## Key Metrics

Monitor these in production:

- **Registration Latency (P99)**: Should be < 50ms
- **Heartbeat Failure Rate**: Should be < 1%
- **Node Count Divergence**: Should match expected deployment
- **Virtual Thread Count**: Should stay constant
- **Direct Buffer Memory**: Should be stable

## Production Challenge

Implement gossip protocol fallback for Redis failure scenarios. See lesson article for details.
