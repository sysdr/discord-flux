#!/bin/bash

echo "🎬 Flux Gateway Cluster - Live Demo"
echo "===================================="
echo ""
echo "This demo will:"
echo "1. Show cluster status and open WebSocket connections (dashboard metrics)"
echo "2. Simulate a gateway failure"
echo "3. Demonstrate graceful recovery"
echo ""
read -p "Press Enter to continue..."

echo ""
echo "📊 Current Cluster Status:"
echo "-------------------------"
docker-compose ps

echo ""
echo "📈 Gateway Registration in Redis:"
echo "--------------------------------"
docker exec flux-registry redis-cli HGETALL gateway:nodes

echo ""
echo "🔌 Opening WebSocket connections so dashboard shows non-zero metrics..."
echo "   (Run: python3 demo_ws_client.py in another terminal, or we run it briefly here)"
if command -v python3 >/dev/null 2>&1; then
    timeout 22 python3 demo_ws_client.py 2>/dev/null || true
else
    echo "   Skipped (python3 not found). Dashboard will show 0 connections until you connect clients."
fi

echo ""
echo "⚠️  Simulating Gateway 2 Failure in 5 seconds..."
sleep 5

echo ""
echo "💥 Stopping Gateway 2 (graceful shutdown)..."
docker stop flux-gateway-2

echo ""
echo "⏳ Waiting for cluster to detect failure (15s)..."
sleep 15

echo ""
echo "📊 Updated Cluster Status:"
echo "-------------------------"
docker exec flux-registry redis-cli HGETALL gateway:nodes

echo ""
echo "✓ Demo complete! Gateway 2 has been removed from the cluster."
echo ""
echo "To restart Gateway 2: docker-compose up -d gateway-2"
echo "To view dashboard: http://localhost:9090/dashboard"
