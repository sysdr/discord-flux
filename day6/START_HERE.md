# 🚀 START THE SERVER HERE

## Quick Start (Choose One Method):

### Method 1: Use the run script (RECOMMENDED)
```bash
cd /home/sdr/git/discord-flux/day6
./run-server.sh
```

### Method 2: Manual start
```bash
cd /home/sdr/git/discord-flux/day6/flux-zombie-reaper
mvn clean compile
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway"
```

### Method 3: Use start script
```bash
cd /home/sdr/git/discord-flux/day6
./start.sh
```

## ⚠️ IMPORTANT:
**The server must be running** before you can access http://localhost:8080

After running the command above, you should see:
```
✅ Flux Gateway started
📊 Dashboard: http://localhost:8080/dashboard
```

## 📊 Once Server is Running:

Open these URLs in your browser:
- **Dashboard**: http://localhost:8080/dashboard
- **Status Page**: http://localhost:8080/status
- **Metrics JSON**: http://localhost:8080/metrics

## 🛑 To Stop the Server:

Press `Ctrl+C` in the terminal where it's running, OR:
```bash
pkill -f FluxGateway
```

## 🔧 Troubleshooting:

### "Connection Refused" Error:
1. **Server is not running** - You MUST start it first using one of the methods above
2. Make sure you see "✅ Flux Gateway started" message
3. Wait 3-5 seconds after starting before accessing the URL

### If server won't start:
```bash
# Kill any old processes
pkill -f FluxGateway

# Check Java version (needs Java 21)
java -version

# Check compilation
cd /home/sdr/git/discord-flux/day6/flux-zombie-reaper
mvn clean compile
```

## 📝 Notes:
- The server runs in the **foreground** (you'll see output)
- Keep the terminal open while using the dashboard
- To run in background, use: `nohup ./run-server.sh &`
