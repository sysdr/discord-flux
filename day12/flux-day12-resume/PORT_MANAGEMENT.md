# Port Management Guide

## Port Configuration

The Flux Gateway uses two ports:
- **Port 8080**: Gateway WebSocket server (`GatewayServer`)
- **Port 8081**: Dashboard HTTP server (`DashboardServer`)

## Port Already in Use Error

If you see `java.net.BindException: Address already in use`, it means another process is using the port.

### Quick Fix

```bash
# Option 1: Use the cleanup script (recommended)
./cleanup.sh

# Option 2: Kill processes manually
lsof -ti :8080 | xargs kill -9
lsof -ti :8081 | xargs kill -9

# Option 3: Kill all gateway Java processes
pkill -f "com.flux.gateway"
```

### Find What's Using the Port

```bash
# Linux/macOS
lsof -i :8080
lsof -i :8081

# Alternative (if lsof not available)
netstat -tulpn | grep -E ':(8080|8081)'
ss -tulpn | grep -E ':(8080|8081)'
```

## Changing Ports

### Method 1: System Properties (Recommended)

```bash
# Start with custom ports
mvn exec:java \
  -Dexec.mainClass="com.flux.gateway.GatewayServer" \
  -Dgateway.port=9090 \
  -Ddashboard.port=9091
```

### Method 2: Environment Variables

```bash
export GATEWAY_PORT=9090
export DASHBOARD_PORT=9091
mvn exec:java -Dexec.mainClass="com.flux.gateway.GatewayServer"
```

### Method 3: Modify Code

Edit the default values in:
- `GatewayServer.java` line 17-19
- `DashboardServer.java` line 11-13

## Best Practices

1. **Always use cleanup.sh before starting**:
   ```bash
   ./cleanup.sh && ./start.sh
   ```

2. **Check ports before starting**:
   ```bash
   lsof -i :8080 :8081
   ```

3. **Use different ports for multiple instances**:
   ```bash
   # Instance 1
   GATEWAY_PORT=8080 DASHBOARD_PORT=8081 ./start.sh
   
   # Instance 2
   GATEWAY_PORT=9090 DASHBOARD_PORT=9091 ./start.sh
   ```

4. **Add port check to start script** (already done):
   The `start.sh` script now checks ports before starting.

## Troubleshooting

### Port still in use after cleanup?

```bash
# Find all Java processes
ps aux | grep java

# Kill specific PID
kill -9 <PID>

# Nuclear option (kill all Java - use with caution!)
pkill java
```

### Permission denied?

```bash
# Use sudo (if you own the process)
sudo lsof -i :8080
sudo kill -9 <PID>

# Or check if you need to run as different user
whoami
```

### Port appears free but still fails?

```bash
# Check if port is in TIME_WAIT state
netstat -an | grep 8080

# Wait 30-60 seconds for TIME_WAIT to clear, or:
# Use SO_REUSEADDR in code (already handled by Java NIO)
```
