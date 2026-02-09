#!/bin/bash
set -e

echo "🔍 Verifying Compaction Strategies"
echo "===================================="
echo ""

cd "$(dirname "$0")"

echo "Testing STCS (Size-Tiered):"
echo "---------------------------"
echo "Expected: High write amplification (30-50x)"
echo "Expected: Many SSTables with mixed time ranges"
echo ""

echo "STCS SSTables count: $(find data/stcs -maxdepth 1 -name '*.db' 2>/dev/null | wc -l)"
du -sh data/stcs 2>/dev/null || echo "No STCS data yet"

echo ""
echo "Testing TWCS (Time-Window):"
echo "---------------------------"
echo "Expected: Low write amplification (2-5x)"
echo "Expected: Fewer SSTables, grouped by time windows"
echo ""

echo "TWCS SSTables count: $(find data/twcs -maxdepth 1 -name '*.db' 2>/dev/null | wc -l)"
du -sh data/twcs 2>/dev/null || echo "No TWCS data yet"

echo ""
echo "💡 Run 'bash demo.sh' to generate data and see the difference"
