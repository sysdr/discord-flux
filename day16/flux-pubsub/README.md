# Flux Day 16: Pub/Sub Primitives

Redis Streams-based pub/sub implementation for decoupling Gateway from API.

## Quick Start

```bash
# 1. Start Redis (if not running)
docker run -d -p 6379:6379 redis:7-alpine

# 2. Start Gateway server
bash lifecycle/start.sh

# 3. Open dashboard
open http://localhost:8080/dashboard.html

# 4. Run load test
bash lifecycle/demo.sh load-test

# 5. Verify setup
bash lifecycle/verify.sh

# 6. Cleanup
bash lifecycle/cleanup.sh
```

## Architecture

- **StreamPublisher**: Async Redis client using Lettuce
- **StreamConsumer**: Virtual Thread-based XREADGROUP polling
- **BoundedEventBuffer**: Lock-free ring buffer for slow consumer protection
- **WebSocketSimulator**: 60 simulated WebSocket connections (50 fast, 10 slow)

## Key Metrics

- Publish/Consume rates
- Average latency (P99)
- Fan-out count per guild
- Dropped messages (slow clients)

## Load Test Modes

```bash
bash lifecycle/demo.sh normal      # 100 messages at 10/sec
bash lifecycle/demo.sh load-test   # 100K messages (stress test)
bash lifecycle/demo.sh burst       # 10K messages in rapid burst
```
