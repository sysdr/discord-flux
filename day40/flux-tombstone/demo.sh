#!/bin/bash

echo "🎬 Running Demo Scenario..."
echo ""
echo "1️⃣  Inserting 2000 messages..."
for i in {1..20}; do
    curl -s -X POST "http://localhost:8080/api/insert?count=100" > /dev/null
    echo -n "."
done
echo " Done!"

sleep 2

echo "2️⃣  Deleting 1000 messages (creating tombstones)..."
for i in {1..10}; do
    curl -s -X POST "http://localhost:8080/api/delete?count=100" > /dev/null
    echo -n "."
done
echo " Done!"

sleep 2

echo "3️⃣  Checking stats before compaction..."
curl -s http://localhost:8080/api/stats | python3 -m json.tool

sleep 3

echo "4️⃣  Forcing compaction..."
curl -s -X POST http://localhost:8080/api/compact
echo ""

sleep 2

echo "5️⃣  Checking stats after compaction..."
curl -s http://localhost:8080/api/stats | python3 -m json.tool

echo ""
echo "✅ Demo complete! Check dashboard at http://localhost:8080"
