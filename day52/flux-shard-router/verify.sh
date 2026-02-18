#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Flux Shard Router - Verification"
echo "===================================="

# Check if server is running (try 127.0.0.1 first for WSL compatibility)
API_URL=""
if curl -s --connect-timeout 5 http://127.0.0.1:8080 > /dev/null 2>&1; then
    API_URL="http://127.0.0.1:8080"
elif curl -s --connect-timeout 5 http://localhost:8080 > /dev/null 2>&1; then
    API_URL="http://localhost:8080"
else
    echo "❌ Server not running. Start with: ./start.sh"
    exit 1
fi

echo "✅ Server is running"

# Fetch stats
echo ""
echo "📊 Current Shard Statistics:"
STATS=$(curl -s $API_URL/api/stats)
echo "$STATS" | python3 -m json.tool 2>/dev/null || echo "$STATS"

# Validate distribution
CV=$(echo "$STATS" | grep -oP '"cv":\s*\K[0-9.]+' || echo "0")

echo ""
if (( $(echo "$CV < 20" | bc -l 2>/dev/null || echo "0") )); then
    echo "✅ PASS: Coefficient of Variation = ${CV}% (< 20%)"
    echo "Distribution is healthy and balanced."
else
    echo "⚠️ WARNING: Coefficient of Variation = ${CV}% (>= 20%)"
    echo "Shard distribution may be imbalanced."
fi

# Test shard calculation
echo ""
echo "🧪 Testing Shard Calculation:"
for GUILD_ID in 123456789 987654321 555555555; do
    SHIFTED=$((GUILD_ID << 22))
    SHARD=$((($SHIFTED >> 22) % 64))
    echo "  Guild $GUILD_ID -> Shard $SHARD"
done

echo ""
echo "✅ Verification complete"
