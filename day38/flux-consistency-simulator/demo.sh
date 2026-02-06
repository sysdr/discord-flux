#!/bin/bash
cd "$(dirname "$0")"
echo "🎬 Running API Demo (writes to server dashboard)..."
if ! curl -s "http://localhost:8080/api/metrics" > /dev/null 2>&1; then
    echo "❌ Server not running! Start with: ./start.sh"
    exit 1
fi
echo "Triggering 50 mixed ONE/QUORUM writes..."
for i in $(seq 1 50); do
    level=$([ $((i % 2)) -eq 0 ] && echo "ONE" || echo "QUORUM")
    curl -s "http://localhost:8080/api/write?level=$level" > /dev/null
done
echo "✅ Demo complete. Check dashboard at http://localhost:8080"
curl -s "http://localhost:8080/api/metrics" | python3 -m json.tool
