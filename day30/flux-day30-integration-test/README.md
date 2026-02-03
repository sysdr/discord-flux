# Flux Day 30: Integration Test - 1,000-User Guild Chat Storm

## Overview
Production-grade integration testing framework for validating a WebSocket Gateway
under realistic Guild chat load (1,000 concurrent users).

## Architecture
- **Gateway**: NIO-based WebSocket server with Virtual Thread workers
- **Load Generator**: Virtual Thread pool simulating 1,000 clients
- **Ring Buffers**: Per-connection isolation for slow consumers
- **Metrics**: Lock-free latency aggregator with percentile calculation
- **Dashboard**: Real-time visualization of connection health and performance

## Prerequisites
- Java 21+ (Virtual Threads required)
- Maven 3.9+
- Redis 7.0+ (`redis-server`)
- 8GB RAM recommended

## Quick Start
```bash
# Start Redis (if not running)
redis-server &

# Generate and run project
./start.sh

# Open dashboard
open http://localhost:9090/dashboard
```

## Performance Targets
- ✅ P95 Latency: < 50ms
- ✅ P99 Latency: < 100ms
- ✅ Throughput: 1,000 messages/sec sustained
- ✅ Zero connection drops (excluding injected failures)

## Project Structure
```
flux-day30-integration-test/
├── src/main/java/com/flux/integrationtest/
│   ├── gateway/
│   │   ├── FluxGateway.java          # Main Gateway (NIO + Virtual Threads)
│   │   ├── Message.java               # Message record with timestamp
│   │   ├── ConnectionState.java       # Per-connection state tracking
│   │   └── MessageRingBuffer.java     # Lock-free ring buffer
│   ├── client/
│   │   ├── WebSocketSimulator.java    # Virtual Thread-based client
│   │   └── LoadTestOrchestrator.java  # Test orchestration
│   ├── metrics/
│   │   └── LatencyAggregator.java     # Percentile calculation
│   ├── dashboard/
│   │   └── DashboardServer.java       # Real-time visualization
│   └── IntegrationTestApp.java        # Main entry point
├── src/test/java/                     # Unit tests
├── start.sh                           # Compile and run
├── verify.sh                          # Check performance targets
├── demo.sh                            # Quick demo scenario
└── cleanup.sh                         # Remove artifacts
```

## Failure Injection
The test automatically injects production failure modes:
- **Slow Consumers**: 10 clients with 5-second read delays
- **Network Partitions**: 100 clients disconnect/reconnect
- **GC Pauses**: Heap pressure simulation

## Monitoring
- **Dashboard**: http://localhost:9090/dashboard
- **VisualVM**: Attach to `IntegrationTestApp` process
- **Redis Monitor**: `redis-cli MONITOR`

## Homework Challenges
1. Optimize ByteBuffer allocation using ThreadLocal pools
2. Implement latency SLA alerting (P95 > 50ms for 10+ seconds)
3. Add connection jitter (Poisson distribution ramp-up)
4. Scale to 10,000 clients
5. Implement graceful degradation (shed low-priority users)

## Troubleshooting
- **High P95 latency**: Check GC pauses with `jstat -gc`
- **Connection drops**: Check TCP backlog with `ss -lnt`
- **Redis errors**: Verify connection pool sizing in Lettuce config
