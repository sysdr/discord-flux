# Flux Gateway - Day 6: Zombie Reaper

Production-grade timeout wheel implementation for killing dead WebSocket connections.

## Quick Start
```bash
# Start the server
./start.sh

# Open dashboard
open http://localhost:8080/dashboard

# Run demo (separate terminal)
./demo.sh

# Verify
./verify.sh

# Cleanup
./cleanup.sh
```

## Architecture

- **TimeoutWheel**: 60-slot ring buffer for O(1) timeout management
- **ZombieReaper**: Virtual thread that advances wheel every second
- **ConnectionRegistry**: Thread-safe connection tracking
- **Dashboard**: Real-time visualization of wheel state

## Key Metrics

- Reaper latency: <1ms for 10k zombies
- Memory: O(N) where N = active connections
- CPU: Single virtual thread, negligible overhead

## Testing
```bash
# Unit tests
mvn test

# Load test (10k connections, 1k zombies)
./demo.sh
```
