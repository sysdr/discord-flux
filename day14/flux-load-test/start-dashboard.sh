#!/bin/bash
set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🚀 Starting Dashboard Server..."
echo "📊 Dashboard will be available at http://localhost:9090"
echo ""

/usr/bin/mvn exec:java -Dexec.mainClass="com.flux.loadtest.dashboard.DashboardStandalone" \
    -Dexec.args="9090" \
    -q
