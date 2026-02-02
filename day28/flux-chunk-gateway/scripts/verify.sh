#!/bin/bash
set -e

echo "🔍 Verifying Flux Gateway Setup..."
echo "===================================="

# Check Java version
echo -n "Java 21+: "
if java -version 2>&1 | grep -q "version \"2[1-9]"; then
    echo "✅"
else
    echo "❌ Java 21+ required"
    exit 1
fi

# Check Redis
echo -n "Redis running: "
if redis-cli ping > /dev/null 2>&1; then
    echo "✅"
else
    echo "❌ Redis not running. Start with: redis-server"
    exit 1
fi

# Check Redis data
echo -n "Test guild seeded: "
if [ "$(redis-cli scard guild:test-guild:members)" -gt 0 ]; then
    MEMBER_COUNT=$(redis-cli scard guild:test-guild:members)
    echo "✅ ($MEMBER_COUNT members)"
else
    echo "❌ Run ./scripts/seed_redis.sh first"
    exit 1
fi

# Check Maven
echo -n "Maven installed: "
if command -v mvn > /dev/null; then
    echo "✅"
else
    echo "❌ Maven required"
    exit 1
fi

# Check if Gateway is running
echo -n "Gateway running: "
if nc -z localhost 9000 2>/dev/null; then
    echo "✅"
else
    echo "⚠️  Not running (start with ./scripts/start.sh)"
fi

# Check if Dashboard is running
echo -n "Dashboard running: "
if nc -z localhost 8080 2>/dev/null; then
    echo "✅"
else
    echo "⚠️  Not running (start with ./scripts/start.sh)"
fi

echo ""
echo "✅ Verification complete!"
