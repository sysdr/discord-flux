#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "======================================"
echo "  FLUX TIME BUCKETING - DEMO MODE"
echo "======================================"
echo

echo "📦 Compiling..."
mvn clean compile -q

echo "🧪 Running naive vs bucketed comparison..."
echo
mvn exec:java -Dexec.mainClass="com.flux.DemoRunner" -q

echo
echo "✅ Demo complete! Key takeaway:"
echo "   Bucketing distributes messages across many partitions"
echo "   instead of massive single partitions per user."
