# Flux Gateway - Day 3: The Event Loop

## Quick Start

1. **Start the server**:
   ```bash
   ./scripts/start.sh
   ```

2. **Open the dashboard** in your browser:
   ```
   http://localhost:8080
   ```

3. **Run the demo** (in another terminal):
   ```bash
   ./scripts/demo.sh
   ```

4. **Verify everything works**:
   ```bash
   ./scripts/verify.sh
   ```

5. **Clean up**:
   ```bash
   ./scripts/cleanup.sh
   ```

## Project Structure

```
flux-event-loop/
├── src/com/flux/gateway/
│   ├── GatewayServer.java         # Entry point
│   ├── core/
│   │   ├── EventLoop.java          # Main NIO event loop
│   │   ├── Connection.java         # Connection state holder
│   │   └── ConnectionState.java    # State enum
│   ├── protocol/
│   │   └── ProtocolHandler.java    # Message framing/parsing
│   └── dashboard/
│       └── Dashboard.java          # HTTP metrics server
├── test/com/flux/gateway/
│   ├── LoadGenerator.java          # Load testing tool
│   └── EventLoopTest.java          # Unit tests
└── scripts/
    ├── start.sh                     # Compile and run
    ├── demo.sh                      # Run demo scenario
    ├── verify.sh                    # Verify installation
    └── cleanup.sh                   # Clean build artifacts
```

## Testing

### Load Testing
```bash
# 1000 connections, 5 messages each
java -cp out/test com.flux.gateway.LoadGenerator 1000 5

# 5000 connections, 10 messages each
java -cp out/test com.flux.gateway.LoadGenerator 5000 10
```

### Monitoring with VisualVM
1. Install VisualVM: `brew install visualvm` (macOS) or download from visualvm.github.io
2. Start the gateway: `./scripts/start.sh`
3. Launch VisualVM and attach to the Java process
4. Monitor:
   - Heap usage (should be flat)
   - Thread count (should be ~2-3)
   - CPU usage during load tests

## Key Metrics to Watch

- **Active Connections**: Current number of connected clients
- **Bytes Read/Written**: Total network I/O
- **Messages Processed**: Total application-level messages
- **GC Activity**: Should be minimal (<100ms pause times)
- **Thread Count**: Should remain constant (~2-3 threads)

## Expected Performance

- Single event loop handles 10,000 concurrent connections
- Message latency p99 < 5ms
- CPU usage < 40% on one core
- Heap usage stable after warmup
- Zero OutOfMemoryError under normal load

## Next Steps

Day 4 will cover back-pressure and flow control when clients send data faster than we can process.
