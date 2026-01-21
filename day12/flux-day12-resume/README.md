# Flux Day 12: Resume Capability

Production-grade WebSocket resume implementation using pure Java 21+.

## Features

- **Lock-free resume logic** with VarHandle atomics
- **Ring buffer** for efficient message history (512 messages per session)
- **Off-heap memory** using DirectByteBuffer (no GC pressure)
- **Virtual Threads** for scalable client handling
- **5-minute TTL** for disconnected sessions
- **Real-time dashboard** with metrics and latency histogram

## Quick Start

```bash
# Generate and build
./project_setup.sh
cd flux-day12-resume

# Start server + dashboard
./start.sh

# In another terminal: run demo
./demo.sh

# Verify everything works
./verify.sh
```

## Architecture

- **SessionState**: Ring buffer with atomic sequence tracking
- **GatewayServer**: NIO selector-based event loop
- **DashboardServer**: HTTP server for real-time metrics
- **LoadTester**: Simulates 100 clients with network partition

## Monitoring

Dashboard: http://localhost:8081/dashboard

Key metrics:
- Resume success rate (target: >99%)
- Average resume latency (target: <50ms)
- Active vs disconnected session count

## Testing

```bash
# Run unit tests
mvn test

# Manual WebSocket test
npm install -g wscat
wscat -c ws://localhost:8080

# Send resume
> {"op":6,"d":{"session_id":"<ID>","seq":10}}
```

## Cleanup

```bash
./cleanup.sh
```
