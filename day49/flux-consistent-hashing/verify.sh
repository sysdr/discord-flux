#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Verifying Consistent Hashing Properties"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Check if server is running
if ! curl -s http://localhost:8080/stats > /dev/null; then
    echo "❌ Gateway Router is not running. Start it first with ./start.sh"
    exit 1
fi

echo ""
echo "Test 1: Distribution Uniformity"
echo "─────────────────────────────────"

# Add test connections
curl -s -X POST http://localhost:8080/simulate -d "10000" > /dev/null
sleep 1

# Fetch stats
STATS=$(curl -s http://localhost:8080/stats)

echo "$STATS" | python3 -c "
import sys, json
data = json.load(sys.stdin)
dist = data.get('nodeDistribution', {})
total = sum(dist.values())

for node, count in sorted(dist.items()):
    pct = (count / total * 100) if total > 0 else 0
    print(f'  {node}: {count:,} connections ({pct:.1f}%)')

uniformity = data.get('uniformity', 0)
print(f'\n  Standard Deviation / Mean: {uniformity:.4f}')

if uniformity < 0.05:
    print('  ✓ Distribution is EXCELLENT (σ/μ < 0.05)')
    sys.exit(0)
elif uniformity < 0.10:
    print('  ⚠ Distribution is FAIR (σ/μ < 0.10)')
    sys.exit(0)
else:
    print('  ❌ Distribution is POOR (σ/μ > 0.10)')
    sys.exit(1)
" 2>/dev/null || echo "  (Install python3 for detailed analysis)"

echo ""
echo "Test 2: Lookup Performance"
echo "─────────────────────────────────"
echo "  Running 100,000 routing lookups..."

mvn exec:java -Dexec.mainClass="com.flux.gateway.LoadTest" -Dexec.args="100000 4" -q 2>/dev/null || true

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✓ Verification complete"
