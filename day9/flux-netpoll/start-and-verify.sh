#!/bin/bash
# Start reactor and verify it's accessible

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "╔════════════════════════════════════════╗"
echo "║   FLUX NETPOLL - START & VERIFY        ║"
echo "╚════════════════════════════════════════╝"
echo ""

# Kill any existing reactors
pkill -9 -f "ReactorMain" 2>/dev/null || true
sleep 2

echo "🔨 Compiling project..."
mvn clean compile -q

echo "🚀 Starting Reactor..."
mvn exec:java -Dexec.mainClass="com.flux.netpoll.ReactorMain" -q > /tmp/reactor_verify.log 2>&1 &
REACTOR_PID=$!

echo "✓ Reactor started (PID: $REACTOR_PID)"
echo ""
echo "⏳ Waiting for reactor to initialize (15 seconds)..."
sleep 15

echo ""
echo "=== Verification ==="
echo ""

# Check process
if ps -p $REACTOR_PID > /dev/null 2>&1; then
    echo "✅ Reactor process is RUNNING"
else
    echo "❌ Reactor process DIED"
    echo "Check logs: /tmp/reactor_verify.log"
    cat /tmp/reactor_verify.log | tail -20
    exit 1
fi

# Check ports
PORT_8080=$(lsof -i :8080 2>/dev/null | grep LISTEN | wc -l)
PORT_9090=$(lsof -i :9090 2>/dev/null | grep LISTEN | wc -l)

if [ "$PORT_8080" -gt 0 ]; then
    echo "✅ Port 8080 is LISTENING"
else
    echo "❌ Port 8080 is NOT listening"
fi

if [ "$PORT_9090" -gt 0 ]; then
    echo "✅ Port 9090 is LISTENING"
else
    echo "❌ Port 9090 is NOT listening"
fi

# Test API
if curl -s http://localhost:8080/api/stats > /dev/null 2>&1; then
    echo "✅ Dashboard API is RESPONDING"
    STATS=$(curl -s http://localhost:8080/api/stats)
    echo "   Current stats: $STATS"
else
    echo "❌ Dashboard API is NOT responding"
fi

# Get WSL IP
WSL_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "unknown")

echo ""
echo "╔════════════════════════════════════════╗"
echo "║         ACCESS INFORMATION             ║"
echo "╚════════════════════════════════════════╝"
echo ""
echo "📊 Dashboard URLs (try in order):"
echo ""
echo "   1. http://localhost:8080/dashboard"
echo "   2. http://127.0.0.1:8080/dashboard"
if [ "$WSL_IP" != "unknown" ]; then
    echo "   3. http://$WSL_IP:8080/dashboard"
fi
echo ""
echo "🔌 Reactor Port: 9090"
echo ""
echo "📝 Process ID: $REACTOR_PID"
echo "📄 Log file: /tmp/reactor_verify.log"
echo ""

# Windows Firewall instructions
echo "🔧 IF CONNECTION REFUSED FROM WINDOWS BROWSER:"
echo ""
echo "   Windows Firewall Fix (PowerShell as Admin):"
echo "   New-NetFirewallRule -DisplayName 'WSL Dashboard' \\"
echo "     -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow"
echo ""
echo "   Or use GUI:"
echo "   1. Win+R → wf.msc"
echo "   2. Inbound Rules → New Rule"
echo "   3. Port → TCP → 8080 → Allow"
echo ""

if curl -s http://localhost:8080/api/stats > /dev/null 2>&1; then
    echo "✅ Reactor is READY and ACCESSIBLE!"
else
    echo "⚠️  Reactor started but API not responding yet"
    echo "   Wait a few more seconds and try again"
fi
