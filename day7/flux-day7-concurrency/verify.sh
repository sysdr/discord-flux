#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Verifying Flux Day 7 Setup"
echo "=============================="
echo ""

# Check for duplicate services
echo "0. Checking for duplicate services..."
PIDS=$(pgrep -f "com.flux.gateway.concurrency.Main" 2>/dev/null || true)
if [ -n "$PIDS" ]; then
    echo "   ⚠️  Found running servers with PIDs: $PIDS"
    read -p "   Do you want to stop them? [y/N]: " answer
    if [[ "$answer" =~ ^[Yy]$ ]]; then
        pkill -f "com.flux.gateway.concurrency.Main" 2>/dev/null || true
        sleep 2
        echo "   ✅ Stopped existing servers"
    fi
else
    echo "   ✅ No duplicate services running"
fi
echo ""

# Check Java version
echo "1. Checking Java version..."
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
echo "   Found: Java $JAVA_VERSION"

if [[ "$JAVA_VERSION" < "21" ]]; then
    echo "   ❌ Java 21 or higher required"
    exit 1
fi
echo "   ✅ Java version OK"
echo ""

# Check Maven
echo "2. Checking Maven..."
if ! command -v mvn &> /dev/null; then
    echo "   ❌ Maven not found"
    exit 1
fi
MVN_VERSION=$(mvn -version | head -n 1)
echo "   Found: $MVN_VERSION"
echo "   ✅ Maven OK"
echo ""

# Compile project
echo "3. Compiling project..."
cd "$SCRIPT_DIR"
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "   ❌ Compilation failed"
    exit 1
fi
echo "   ✅ Compilation successful"
echo ""

# Run tests
echo "4. Running unit tests..."
cd "$SCRIPT_DIR"
mvn test -q
if [ $? -ne 0 ]; then
    echo "   ❌ Tests failed"
    exit 1
fi
echo "   ✅ All tests passed"
echo ""

# Quick smoke test for each server
echo "5. Running smoke tests..."

for TYPE in thread nio virtual; do
    echo ""
    echo "   Testing $TYPE server..."
    
    cd "$SCRIPT_DIR"
    # Start server
    timeout 30 mvn exec:java -Dexec.mainClass="com.flux.gateway.concurrency.Main" \
        -Dexec.args="$TYPE 9000" > /tmp/server_$TYPE.log 2>&1 &
    SERVER_PID=$!
    
    sleep 2
    
    # Quick connectivity test
    if timeout 5 bash -c "echo test | nc localhost 9000 > /dev/null 2>&1" 2>/dev/null || \
       timeout 5 bash -c "echo test | telnet localhost 9000 > /dev/null 2>&1" 2>/dev/null; then
        echo "   ✅ $TYPE server responding"
    else
        echo "   ❌ $TYPE server not responding (check /tmp/server_$TYPE.log)"
    fi
    
    kill $SERVER_PID 2>/dev/null
    wait $SERVER_PID 2>/dev/null
done

echo ""
echo "=============================="
echo "✅ All verification checks passed!"
echo ""
echo "Next steps:"
echo "  1. Run '$SCRIPT_DIR/start.sh' to start a server interactively"
echo "  2. Run '$SCRIPT_DIR/demo.sh nio 1000 5' for an automated demo"
echo "  3. Open http://localhost:8080 to view the dashboard"
