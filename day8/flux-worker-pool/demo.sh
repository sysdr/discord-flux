#!/bin/bash
echo "🔥 Running load test (1000 clients × 100 messages = 100k total)..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.LoadTest" -q

echo ""
echo "✅ Load test completed!"
echo "📊 Check dashboard at http://localhost:9090"
