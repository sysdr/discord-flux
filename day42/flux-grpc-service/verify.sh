#!/bin/bash

echo "🔍 Verifying Flux gRPC Service"
echo "=============================="
echo ""

# Check gRPC server
if nc -z localhost 9090 2>/dev/null; then
    echo "✅ gRPC server responding on port 9090"
else
    echo "❌ gRPC server not responding"
    exit 1
fi

# Check metrics endpoint
if curl -s http://localhost:8080/metrics > /dev/null; then
    echo "✅ Metrics endpoint responding"
    
    # Parse metrics
    METRICS=$(curl -s http://localhost:8080/metrics)
    TOTAL=$(echo $METRICS | grep -o '"total_requests": [0-9]*' | grep -o '[0-9]*')
    DB_STATUS=$(echo $METRICS | grep -o '"db_connected": [a-z]*' | grep -o '[a-z]*')
    
    echo "   Total Requests: $TOTAL"
    echo "   DB Connected: $DB_STATUS"
else
    echo "❌ Metrics endpoint not responding"
    exit 1
fi

# Test reflection
if command -v grpcurl &> /dev/null; then
    echo ""
    echo "📋 Testing gRPC Reflection:"
    grpcurl -plaintext localhost:9090 list | head -5
else
    echo "⚠️  grpcurl not installed (optional)"
fi

echo ""
echo "✅ All checks passed!"
echo ""
echo "Next steps:"
echo "  1. Run: ./demo.sh"
echo "  2. Open: http://localhost:8080"
echo "  3. Test: grpcurl -plaintext localhost:9090 list"
