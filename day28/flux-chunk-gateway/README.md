# Flux Gateway - Day 28: Guild Member Chunks

Production-grade WebSocket gateway implementing Discord's lazy loading protocol for guild members.

## Quick Start

```bash
# 1. Seed Redis with test data
./scripts/seed_redis.sh

# 2. Start gateway + dashboard
./scripts/start.sh

# 3. Open dashboard
open http://localhost:8080

# 4. Run demo scenarios
./scripts/demo.sh
```

## Verification

```bash
./scripts/verify.sh
```

## Architecture

- **Ring Buffer Dispatcher**: Lock-free queue for chunk requests
- **Virtual Thread Workers**: Scales to 50K+ concurrent operations
- **Cursor-Based Pagination**: Zero heap allocation using Redis SCAN
- **Backpressure Control**: Max 5 in-flight chunks per connection

## Performance Targets

- **Latency**: < 100ms p95 for chunk requests
- **Throughput**: 10,000 concurrent chunks
- **Memory**: < 6GB heap under full load
- **GC**: < 20ms pause times

## Cleanup

```bash
./scripts/cleanup.sh
```

## Homework

Implement batched pipelining using Lettuce's `RedisFuture` to reduce latency from 85ms to < 50ms.
