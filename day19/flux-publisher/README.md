# Flux Publisher - Lesson 19

High-throughput message publisher using Virtual Threads and async Redis.

## Architecture

- **Virtual Threads**: Handle 100K concurrent HTTP requests
- **Lettuce Async Client**: Non-blocking Redis operations
- **Token Bucket Rate Limiter**: Lock-free backpressure using VarHandle
- **Guild-Centric Routing**: Efficient message fan-out

## Quick Start

```bash
# 1. Start Redis
redis-server

# 2. Generate and start project
./project_setup.sh
cd flux-publisher
./start.sh

# 3. Open dashboard
open http://localhost:8080/dashboard

# 4. Run demo
./demo.sh

# 5. Run verification
./verify.sh
```

## API

**POST /messages**
```json
{
  "guild_id": "guild-123",
  "channel_id": "channel-456",
  "user_id": "user-789",
  "content": "Hello, world!"
}
```

**Response: 202 Accepted**
```json
{
  "id": "1704123456789-0"
}
```

## Monitoring

- Dashboard: http://localhost:8080/dashboard
- Metrics API: http://localhost:8080/api/metrics
- Redis: `redis-cli XINFO STREAM guild:guild-123:messages`

## Load Testing

```bash
# 10K requests, 1K concurrency
mvn test-compile exec:java \
    -Dexec.mainClass="com.flux.publisher.LoadTestClient" \
    -Dexec.args="10000 1000" \
    -Dexec.classpathScope=test
```

## Cleanup

```bash
./cleanup.sh
```
