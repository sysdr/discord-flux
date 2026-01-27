#!/bin/bash
set -e

echo "🔍 Verifying Publisher Setup..."
echo ""

# 1. Check Redis connection
echo "1. Checking Redis..."
if redis-cli ping > /dev/null 2>&1; then
    echo "   ✅ Redis is running"
else
    echo "   ❌ Redis not running. Start with: redis-server"
    exit 1
fi

# 2. Check server is running
echo "2. Checking Publisher API..."
if curl -s http://localhost:8080/dashboard > /dev/null; then
    echo "   ✅ Publisher API is running"
else
    echo "   ❌ Publisher API not running. Start with: ./start.sh"
    exit 1
fi

# 3. Run load test
echo "3. Running load test (10,000 requests)..."
mvn test-compile exec:java \
    -Dexec.mainClass="com.flux.publisher.LoadTestClient" \
    -Dexec.args="10000 1000" \
    -Dexec.classpathScope=test -q

echo ""
echo "4. Checking Redis streams..."
GUILD_COUNT=$(redis-cli KEYS "guild:*:messages" | wc -l)
echo "   Found $GUILD_COUNT guild streams in Redis"

echo ""
echo "5. Sample stream data:"
SAMPLE_KEY=$(redis-cli KEYS "guild:*:messages" | head -1)
if [ -n "$SAMPLE_KEY" ]; then
    echo "   Stream: $SAMPLE_KEY"
    redis-cli XLEN "$SAMPLE_KEY"
    redis-cli XRANGE "$SAMPLE_KEY" - + COUNT 1
fi

echo ""
echo "✅ Verification complete!"
echo ""
echo "View live metrics: http://localhost:8080/dashboard"
