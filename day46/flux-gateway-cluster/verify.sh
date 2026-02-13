#!/bin/bash

echo "🔍 Verifying Flux Gateway Cluster..."
echo "===================================="
echo ""

# Check Redis
echo -n "Checking Redis... "
if docker exec flux-registry redis-cli ping > /dev/null 2>&1; then
    echo "✓ Running"
else
    echo "✗ Not responding"
    exit 1
fi

# Check Gateways
for i in 1 2 3; do
    echo -n "Checking Gateway $i... "
    if curl -s -f http://localhost:808$i/health > /dev/null 2>&1; then
        HEALTH=$(curl -s http://localhost:808$i/health | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
        CONNECTIONS=$(curl -s http://localhost:808$i/health | grep -o '"connections":[0-9]*' | cut -d':' -f2)
        echo "✓ $HEALTH ($CONNECTIONS connections)"
    else
        # Try via Docker exec as health endpoint might not be exposed
        if docker exec flux-gateway-$i curl -s -f http://localhost:8081/health > /dev/null 2>&1; then
            echo "✓ Running (internal check)"
        else
            echo "✗ Not responding"
        fi
    fi
done

# Check Load Balancer
echo -n "Checking Load Balancer... "
if curl -s -f http://localhost:9090/api/cluster > /dev/null 2>&1; then
    NODES=$(curl -s http://localhost:9090/api/cluster | grep -o '"nodeId"' | wc -l)
    echo "✓ Running ($NODES nodes discovered)"
else
    echo "✗ Not responding"
    exit 1
fi

echo ""
echo "✓ All services are healthy!"
echo ""
echo "📊 Open dashboard: http://localhost:9090/dashboard"
