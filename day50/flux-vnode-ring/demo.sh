#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎬 Running Flux Virtual Node Ring Demo"
echo "======================================"
echo ""

# Compile
echo "Building project..."
mvn clean compile -q

echo ""
echo "📊 Demo Scenario: Observing Connection Distribution"
echo ""

# Create a demo driver
cat > src/main/java/com/flux/gateway/Demo.java << 'DEMO_EOF'
package com.flux.gateway;

import com.flux.gateway.ring.ConsistentHashRing;
import java.util.Map;
import java.util.UUID;

public class Demo {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Step 1: Create ring with 5 servers\n");
        
        ConsistentHashRing ring = new ConsistentHashRing(150);
        for (int i = 1; i <= 5; i++) {
            ring.addServer("gateway-0" + i);
        }
        
        System.out.println("Step 2: Route 50,000 connections\n");
        for (int i = 0; i < 50_000; i++) {
            String connId = "conn-" + UUID.randomUUID();
            String server = ring.findServer(connId);
            ring.recordConnection(server);
        }
        
        printStats("Initial Distribution (5 servers)", ring);
        
        System.out.println("\n" + "=".repeat(60) + "\n");
        System.out.println("Step 3: Add 6th server and observe rebalancing\n");
        
        ring.addServer("gateway-06");
        
        for (int i = 0; i < 10_000; i++) {
            String connId = "new-conn-" + UUID.randomUUID();
            String server = ring.findServer(connId);
            ring.recordConnection(server);
        }
        
        printStats("After Adding Server (6 servers)", ring);
        
        System.out.println("\n✅ Demo complete! Notice how variance remains low.");
    }
    
    private static void printStats(String title, ConsistentHashRing ring) {
        var stats = ring.getStats();
        System.out.println("📊 " + title);
        System.out.println("   Total Connections: " + stats.totalConnections());
        System.out.println("   Std Deviation: " + String.format("%.2f", stats.stdDev()));
        System.out.println("   Variance: " + String.format("%.2f%%", stats.variancePercent()));
        System.out.println("\n   Distribution:");
        stats.distribution().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                double pct = (double) e.getValue() / stats.totalConnections() * 100;
                System.out.printf("     %s: %,6d (%.2f%%)%n", 
                    e.getKey(), e.getValue(), pct);
            });
    }
}
DEMO_EOF

# Recompile to include Demo class
mvn compile -q

# Run demo
mvn exec:java -Dexec.mainClass="com.flux.gateway.Demo" -q

echo ""
echo "🎯 Next: Run './start.sh' to launch the interactive dashboard"
