# Day 14: Load Test Framework

## Quick Start

1. **Build and Run**:
```bash
   ./start.sh
```

2. **View Dashboard**:
   Open http://localhost:9090

3. **Run Demo Scenario**:
```bash
   ./demo.sh
```

4. **Verify Health**:
```bash
   ./verify.sh
```

5. **Cleanup**:
```bash
   ./cleanup.sh
```

## What You'll Learn

- Virtual Thread-based concurrency for 10k+ clients
- Lock-free metrics with LongAdder
- WebSocket handshake implementation
- Memory profiling under load
- Production debugging with jcmd/VisualVM

## Key Files

- `WebSocketClient.java` - NIO WebSocket client
- `LoadTestRunner.java` - Test orchestrator
- `MetricsCollector.java` - Lock-free counters
- `DashboardServer.java` - Real-time visualization

## Prerequisites

- Java 21+
- Maven 3.9+
- ulimit -n 65535 (for 10k connections)
- Gateway running on localhost:8080

## Troubleshooting

**"Unable to create native thread"**: Increase ulimit
**"Connection refused"**: Start gateway first
**Threads > 1000**: Check for synchronized blocks (should use locks)
