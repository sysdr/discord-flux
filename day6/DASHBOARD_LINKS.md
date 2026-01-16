# 📊 Flux Gateway Dashboard Links

## 🚀 Server Configuration:
- **Port**: 8080
- **Host**: localhost (127.0.0.1)

## 📍 Available Endpoints:

### 1. **Main Dashboard** (Interactive UI)
```
http://localhost:8080/dashboard
```
- Real-time metrics visualization
- Timeout wheel visualization
- Demo controls (Spawn connections, Simulate partition, Run demo, Reset)
- Auto-refreshes every 1 second

### 2. **Status Page** (Detailed Status View)
```
http://localhost:8080/status
```
- Complete connection status
- Timeout wheel statistics
- All metrics overview
- Auto-refreshes every 5 seconds
- Links back to dashboard

### 3. **Metrics JSON API**
```
http://localhost:8080/metrics
```
- Returns JSON with all metrics
- Useful for programmatic access
- Format: JSON

### 4. **Demo Endpoints** (POST requests):
- `/demo/spawn?count=N` - Spawn N connections
- `/demo/partition` - Simulate network partition (500 zombies)
- `/demo/run` - Run full demo scenario
- `/demo/reset` - Reset all connections

## 🔗 Quick Links:

**Primary Dashboard:**
👉 **http://localhost:8080/dashboard**

**Status Page:**
👉 **http://localhost:8080/status**

**Metrics API:**
👉 **http://localhost:8080/metrics**

## ⚠️ Important Notes:

1. **Server must be running** - Start it with:
   ```bash
   cd /home/sdr/git/discord-flux/day6
   ./run-server.sh
   ```

2. **Wait for startup message:**
   ```
   ✅ Flux Gateway started
   📊 Dashboard: http://localhost:8080/dashboard
   ```

3. **If using WSL or remote access**, you may need to:
   - Use `127.0.0.1:8080` instead of `localhost:8080`
   - Or access via your machine's IP address

4. **Browser compatibility:**
   - Works in Chrome, Firefox, Edge, Safari
   - Requires JavaScript enabled

## 🧪 Test if Server is Running:

```bash
curl http://localhost:8080/metrics
```

If you get JSON output, the server is running!
