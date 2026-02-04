#!/bin/bash

set -e

# Change to script directory for full-path invocation
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 Verification Test Suite"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo "1️⃣  Running JUnit Tests..."
mvn test -q
echo ""

echo "2️⃣  Generating Test IDs via API..."
for i in {1..10}; do
    RESPONSE=$(curl -s http://localhost:8080/api/id)
    ID=$(echo $RESPONSE | grep -o '"id": [0-9]*' | grep -o '[0-9]*')
    echo "  Generated ID: $ID"
done
echo ""

echo "3️⃣  Parsing Sample ID..."
SAMPLE_ID=$(curl -s http://localhost:8080/api/id | grep -o '"id": [0-9]*' | grep -o '[0-9]*')
curl -s "http://localhost:8080/api/parse?id=$SAMPLE_ID" | python3 -m json.tool
echo ""

echo "4️⃣  Fetching Metrics..."
curl -s http://localhost:8080/api/metrics | python3 -m json.tool
echo ""

echo "✅ Verification Complete!"
