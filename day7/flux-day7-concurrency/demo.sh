#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ $# -lt 2 ]; then
    echo "Usage: $SCRIPT_DIR/demo.sh <thread|nio|virtual> <num_connections> [messages_per_connection]"
    echo "Example: $SCRIPT_DIR/demo.sh nio 10000 10"
    exit 1
fi

TYPE=$1
CONNECTIONS=$2
MESSAGES=${3:-10}

# Check for duplicate services
check_duplicate_services() {
    local PIDS=$(pgrep -f "com.flux.gateway.concurrency.Main" 2>/dev/null || true)
    if [ -n "$PIDS" ]; then
        echo "⚠️  Warning: Found running servers with PIDs: $PIDS"
        echo "Stopping existing servers..."
        pkill -f "com.flux.gateway.concurrency.Main" 2>/dev/null || true
        sleep 2
    fi
}

check_duplicate_services

echo "🎬 Running demo: $TYPE server with $CONNECTIONS connections"

# Compile if needed
if [ ! -d "$SCRIPT_DIR/target/classes" ]; then
    echo "📦 Compiling project..."
    cd "$SCRIPT_DIR"
    mvn clean compile -q
fi

# Start server in background
echo "🚀 Starting $TYPE server..."
cd "$SCRIPT_DIR"
mvn exec:java -Dexec.mainClass="com.flux.gateway.concurrency.Main" \
    -Dexec.args="$TYPE 9000" > "$SCRIPT_DIR/server.log" 2>&1 &
SERVER_PID=$!

echo "   Server PID: $SERVER_PID"
sleep 3

# Check if server started
if ! kill -0 $SERVER_PID 2>/dev/null; then
    echo "❌ Server failed to start. Check $SCRIPT_DIR/server.log"
    cat "$SCRIPT_DIR/server.log" 2>/dev/null || true
    exit 1
fi

# Wait for server to be ready
for i in {1..10}; do
    if curl -s http://localhost:8080/metrics > /dev/null 2>&1; then
        break
    fi
    if [ $i -eq 10 ]; then
        echo "❌ Dashboard not responding after 10 seconds"
        kill $SERVER_PID 2>/dev/null
        exit 1
    fi
    sleep 1
done

echo "✅ Server started successfully"
echo ""
echo "🔥 Starting load test..."
echo "   Connections: $CONNECTIONS"
echo "   Messages per connection: $MESSAGES"
echo ""

cd "$SCRIPT_DIR"
mvn exec:java -Dexec.mainClass="com.flux.gateway.concurrency.metrics.LoadTestClient" \
    -Dexec.args="localhost 9000 $CONNECTIONS $MESSAGES"

echo ""
echo "📊 Final server metrics:"
sleep 2

# Validate metrics are non-zero
validate_metrics() {
    local METRICS=$(curl -s http://localhost:8080/metrics 2>/dev/null)
    if [ -z "$METRICS" ]; then
        echo "❌ Could not fetch metrics"
        return 1
    fi
    
    echo "$METRICS" | python3 -m json.tool 2>/dev/null || echo "$METRICS"
    echo ""
    
    # Check if values are non-zero (at least one metric should be > 0)
    local TOTAL_CONN=$(echo "$METRICS" | grep -o '"totalConnections":[0-9]*' | cut -d: -f2 || echo "0")
    local MSGS_RECV=$(echo "$METRICS" | grep -o '"messagesReceived":[0-9]*' | cut -d: -f2 || echo "0")
    
    if [ "$TOTAL_CONN" = "0" ] && [ "$MSGS_RECV" = "0" ]; then
        echo "⚠️  Warning: Metrics are zero - demo may not have generated traffic"
    else
        echo "✅ Metrics validation: Values are non-zero"
    fi
}

validate_metrics

echo ""
read -p "Press Enter to stop server..."

kill $SERVER_PID 2>/dev/null
wait $SERVER_PID 2>/dev/null

echo "✅ Demo completed"
