# Quick Start Guide - Flux Gateway Server

## To Start the Server:

### Option 1: From root directory
```bash
cd /home/sdr/git/discord-flux/day6
./start.sh
```

### Option 2: From project directory
```bash
cd /home/sdr/git/discord-flux/day6/flux-zombie-reaper
./start.sh
```

### Option 3: Manual start
```bash
cd /home/sdr/git/discord-flux/day6/flux-zombie-reaper
mvn clean compile
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway"
```

## After Server Starts:

You should see:
```
✅ Flux Gateway started
📊 Dashboard: http://localhost:8080/dashboard
```

Then open in your browser:
- **Dashboard**: http://localhost:8080/dashboard
- **Status Page**: http://localhost:8080/status  
- **Metrics JSON**: http://localhost:8080/metrics

## To Stop the Server:

Press `Ctrl+C` in the terminal where it's running, or:
```bash
pkill -f FluxGateway
```

## Troubleshooting:

### If you get "Connection Refused":

1. **Make sure server is running:**
   ```bash
   pgrep -f FluxGateway
   ```

2. **Check port 8080:**
   ```bash
   lsof -i :8080
   ```

3. **Kill any old processes:**
   ```bash
   pkill -f FluxGateway
   ```

4. **Compile and start fresh:**
   ```bash
   cd /home/sdr/git/discord-flux/day6/flux-zombie-reaper
   mvn clean compile
   mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway"
   ```

### If compilation fails:

Check Java version (needs Java 21):
```bash
java -version
```

If wrong version, install Java 21:
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```
