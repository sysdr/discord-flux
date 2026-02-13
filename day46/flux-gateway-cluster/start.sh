#!/bin/bash
cd "$(dirname "$0")"

echo "🚀 Starting Flux Gateway Cluster..."
echo "=================================="

# Build and start services
docker-compose up --build -d

echo ""
echo "⏳ Waiting for services to be ready..."
sleep 5

echo ""
echo "✓ Cluster started successfully!"
echo ""
echo "📊 Dashboard: http://localhost:9090/dashboard"
echo "🔌 WebSocket: ws://localhost:8080/ws"
echo ""
echo "To view logs: docker-compose logs -f"
echo "To stop: ./cleanup.sh"
