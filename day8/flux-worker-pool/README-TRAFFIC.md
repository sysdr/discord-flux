# Keeping Metrics Active

## Problem
When there's no traffic, the dashboard shows zeros for all metrics. This is expected behavior, but can be confusing.

## Solution
The project now includes automatic traffic generation to keep metrics populated.

## Quick Start with Traffic

**Option 1: Start server with automatic traffic (Recommended)**
```bash
cd flux-worker-pool
./start-with-traffic.sh
```

This will:
- Start the Gateway Server
- Automatically begin sending background traffic
- Keep metrics continuously updated

**Option 2: Manual traffic generation**
```bash
# Start server normally
./start.sh

# In another terminal, run:
./generate-traffic.sh
```

## Stopping Traffic Generator

To stop the background traffic generator:
```bash
pkill -f "python3.*traffic"
```

Or stop everything:
```bash
./cleanup.sh
```

## Current Status

- ✅ Server is running with automatic traffic generation
- ✅ Metrics are being continuously updated
- ✅ Dashboard shows real-time data at http://localhost:9090

## Metrics Explained

- **Queue Depth**: Number of tasks waiting to be processed (should be 0 or low)
- **Processed Tasks**: Total tasks processed since server start (increments continuously)
- **Rejected Tasks**: Tasks rejected due to full queue (should be 0)
- **p50 Latency**: Median processing time (50th percentile)
- **p99 Latency**: 99th percentile processing time (worst case for 99% of requests)
