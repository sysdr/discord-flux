#!/bin/bash

echo "🔍 Verifying Flux Gateway Installation"
echo ""

# Check Java version
echo "1. Checking Java version..."
java -version 2>&1 | head -n 1

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "❌ Java 21+ required"
    exit 1
fi
echo "✅ Java version OK"
echo ""

# Check if server is running
echo "2. Checking if gateway is running on port 9001..."
nc -z localhost 9001 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ Gateway is running"
else
    echo "⚠️  Gateway not running. Start with: bash start.sh"
fi
echo ""

# Check dashboard
echo "3. Checking dashboard on port 8080..."
nc -z localhost 8080 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ Dashboard is accessible at http://localhost:8080"
else
    echo "⚠️  Dashboard not running"
fi
echo ""

# Compile and run tests
echo "4. Running unit tests..."
javac --enable-preview -source 21 \
    -d target/test-classes \
    -cp "target/classes:src/test/java" \
    src/test/java/com/flux/gateway/FrameParserTest.java 2>/dev/null

if [ $? -eq 0 ]; then
    # Download JUnit if not present
    if [ ! -f "junit-platform-console-standalone-1.10.1.jar" ]; then
        echo "Downloading JUnit..."
        curl -L -O https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.1/junit-platform-console-standalone-1.10.1.jar
    fi
    
    java --enable-preview -jar junit-platform-console-standalone-1.10.1.jar \
        -cp target/classes:target/test-classes \
        --select-class com.flux.gateway.FrameParserTest 2>/dev/null
    
    if [ $? -eq 0 ]; then
        echo "✅ All tests passed"
    else
        echo "⚠️  Some tests failed"
    fi
else
    echo "⚠️  Test compilation failed (JUnit might be missing)"
fi
echo ""

echo "🎉 Verification complete!"
echo ""
echo "Quick Start:"
echo "  1. bash start.sh         # Start the gateway"
echo "  2. Open http://localhost:8080  # View dashboard"
echo "  3. bash demo.sh          # Run demo scenario"
