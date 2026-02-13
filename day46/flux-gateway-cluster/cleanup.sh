#!/bin/bash

echo "🧹 Cleaning up Flux Gateway Cluster..."
echo "======================================"

# Stop and remove containers
docker-compose down -v

# Remove build artifacts
rm -rf gateway/target loadbalancer/target

echo ""
echo "✓ Cleanup complete!"
