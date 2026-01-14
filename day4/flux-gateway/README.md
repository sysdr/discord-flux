# Flux Gateway - Day 4: Protocol Design

Production-grade WebSocket Gateway with Opcode-based protocol.

## Features

- ✅ Zero-allocation hot path (Heartbeat handling)
- ✅ Virtual Threads for concurrency
- ✅ NIO Selector for event-driven I/O
- ✅ Live metrics dashboard
- ✅ Protocol testing suite

## Quick Start

```bash
# Start gateway + dashboard
./start.sh

# Verify installation
./verify.sh

# Run demo (100 concurrent clients)
./demo.sh

# Cleanup
./cleanup.sh
```

## Architecture

- **Gateway Server**: Port 9000 (raw sockets)
- **Dashboard**: Port 8080 (HTTP)

## Protocol Opcodes

- `0` - Dispatch (server → client)
- `1` - Heartbeat (client → server)
- `2` - Identify (client → server)
- `10` - Hello (server → client)
- `11` - HeartbeatAck (server → client)

## Monitoring

Open http://localhost:8080 for live dashboard.

Monitor JVM with:
```bash
jconsole $(cat gateway.pid)
```

## Testing

```bash
mvn test
```
