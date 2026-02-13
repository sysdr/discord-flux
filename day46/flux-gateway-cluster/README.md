# Flux Gateway Cluster

A production-grade distributed WebSocket gateway cluster built from first principles using Java 21 NIO.

## Architecture

- **Gateway Nodes (3)**: WebSocket servers handling client connections
- **Service Registry (Redis)**: Cluster membership and health tracking  
- **Load Balancer**: TCP proxy with consistent hashing for session affinity
- **Dashboard**: Real-time cluster monitoring UI

## Quick Start

```bash
# Start the cluster
./start.sh

# Verify health
./verify.sh

# Run demo scenario
./demo.sh

# View dashboard
open http://localhost:9090/dashboard

# Stop cluster
./cleanup.sh
```

## Key Features

- **Graceful Shutdown**: 30-second connection drain on SIGTERM
- **Consistent Hashing**: Maintains session affinity across failures
- **Health Checks**: 5-second heartbeats with 15-second TTL
- **Resource Limits**: Per-container CPU and memory constraints
- **Real-time Monitoring**: Live dashboard with cluster topology

## Testing Failure Scenarios

### Kill a Gateway
```bash
docker stop flux-gateway-2
# Watch dashboard show node as DEAD
# Existing connections drain gracefully
# New connections route to healthy nodes
```

### Drain a Gateway
```bash
docker exec flux-gateway-1 kill -TERM 1
# Node marks itself as DRAINING
# Stops accepting new connections
# Waits for existing connections to close
```

### Network Partition
```bash
docker network disconnect flux-network flux-gateway-3
# Gateway 3 isolated but still running
# Health checks fail after 15s
# Load balancer removes from routing table
```

## Monitoring

### JVM Metrics
```bash
# Attach jconsole
docker exec flux-gateway-1 jcmd

# Check GC stats
docker exec flux-gateway-1 jstat -gc 1 1000
```

### Redis Inspection
```bash
# View cluster membership
docker exec flux-registry redis-cli HGETALL gateway:nodes

# Monitor commands
docker exec flux-registry redis-cli MONITOR
```

## Production Considerations

See lesson article for:
- Zero-downtime rolling updates
- Connection draining strategies  
- Resource tuning for 1M+ connections
- Graceful shutdown implementation

## License

MIT
