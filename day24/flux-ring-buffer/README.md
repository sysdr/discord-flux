# Flux Ring Buffer - Lesson 24

## Overview
Production-grade ring buffer implementation for handling slow WebSocket clients.
Uses lock-free VarHandle atomics to prevent GC pressure and thread contention.

## Quick Start

1. **Build and Run**:
   ```bash
   ./start.sh
   ```

2. **Open Dashboard**:
   Navigate to http://localhost:8080

3. **Observe**:
   - Green cells: Fast clients (low buffer utilization)
   - Red cells: Slow clients (high buffer utilization)
   - Watch backpressure events increment

4. **Run Demo Scenario**:
   ```bash
   ./demo.sh
   ```

5. **Verify Implementation**:
   ```bash
   ./verify.sh
   ```

## Architecture

```
Event Generator (1000 msg/sec)
        ↓
    Gateway
        ↓
Ring Buffer (per client, 256 slots)
        ↓
Client Socket (varies: 1k-10k msg/sec)
```

## Key Metrics

- **Buffer Utilization**: % of slots filled
- **Backpressure Events**: Failed writes due to full buffer
- **Dropped Messages**: Total messages lost
- **Messages Sent**: Successfully delivered messages

## Cleanup

```bash
./cleanup.sh
```
