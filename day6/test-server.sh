#!/bin/bash

echo "🔍 Testing Flux Gateway Server Setup..."
echo ""

# Check if project exists
if [ ! -d "flux-zombie-reaper" ]; then
    echo "❌ Project directory not found. Run ./build.sh first."
    exit 1
fi

cd flux-zombie-reaper || exit 1

# Check Java
echo "☕ Checking Java..."
if ! command -v java &> /dev/null; then
    echo "❌ Java not found!"
    exit 1
fi
java -version 2>&1 | head -1
echo ""

# Check Maven
echo "🔨 Checking Maven..."
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found!"
    exit 1
fi
mvn --version 2>&1 | head -1
echo ""

# Compile
echo "🔨 Compiling..."
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi
echo "✅ Compilation successful"
echo ""

# Check if port is available
echo "🔍 Checking port 8080..."
if lsof -i :8080 >/dev/null 2>&1 || netstat -tlnp 2>/dev/null | grep -q :8080; then
    echo "⚠️  Port 8080 is already in use!"
    echo "💡 Run: pkill -f FluxGateway"
    exit 1
fi
echo "✅ Port 8080 is available"
echo ""

echo "✅ All checks passed!"
echo ""
echo "🚀 To start the server, run:"
echo "   cd flux-zombie-reaper"
echo "   mvn exec:java -Dexec.mainClass=\"com.flux.gateway.FluxGateway\""
echo ""
echo "   OR use:"
echo "   ./start-server.sh"
echo ""
