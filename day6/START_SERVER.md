# How to Start the Flux Gateway Server

## Quick Start

1. **Navigate to the project directory:**
   ```bash
   cd /home/sdr/git/discord-flux/day6
   ```

2. **Start the server:**
   ```bash
   ./start-server.sh
   ```
   
   OR manually:
   ```bash
   cd flux-zombie-reaper
   mvn clean compile
   mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway"
   ```

3. **Access the dashboard:**
   - Dashboard: http://localhost:8080/dashboard
   - Status Page: http://localhost:8080/status
   - Metrics JSON: http://localhost:8080/metrics

## Troubleshooting

### If you get "Connection Refused":

1. **Check if server is running:**
   ```bash
   pgrep -f FluxGateway
   ```

2. **Check if port 8080 is in use:**
   ```bash
   lsof -i :8080
   # or
   netstat -tlnp | grep :8080
   ```

3. **Kill any existing processes:**
   ```bash
   pkill -f FluxGateway
   ```

4. **Compile and start fresh:**
   ```bash
   cd flux-zombie-reaper
   mvn clean compile
   mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway"
   ```

### If compilation fails:

1. **Check Java version (needs Java 21):**
   ```bash
   java -version
   ```

2. **Check Maven:**
   ```bash
   mvn --version
   ```

3. **Clean and rebuild:**
   ```bash
   cd flux-zombie-reaper
   mvn clean
   mvn compile
   ```

## Running in Background

To run the server in the background:

```bash
cd /home/sdr/git/discord-flux/day6/flux-zombie-reaper
nohup mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway" > /tmp/flux-gateway.log 2>&1 &
```

Check the log:
```bash
tail -f /tmp/flux-gateway.log
```

Stop the background server:
```bash
pkill -f FluxGateway
```
