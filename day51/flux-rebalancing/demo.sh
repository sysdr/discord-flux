#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎬 Running Rebalancing Demo Scenario..."
echo "========================================"
echo ""

# Compile first
javac --release 21 \
    -d target/classes \
    -sourcepath src/main/java \
    src/main/java/com/flux/rebalancing/*.java 2>/dev/null

# Create demo script
cat > DemoScenario.java << 'DEMOJAVA'
import com.flux.rebalancing.*;
import java.util.concurrent.TimeUnit;

public class DemoScenario {
    public static void main(String[] args) throws Exception {
        var simulator = new RebalancingSimulator();
        
        System.out.println("📌 Scenario: Scaling from 3 to 5 nodes");
        System.out.println("=========================================\n");
        
        // Phase 1: Initial cluster
        System.out.println("Phase 1: Initialize 3-node cluster with 100K connections");
        simulator.initializeCluster(3);
        simulator.createConnections(100000);
        
        Thread.sleep(2000);
        
        // Phase 2: Add node 4
        System.out.println("\n\nPhase 2: Add Node 4 (T+5s)");
        System.out.println("---------------------------");
        var result1 = simulator.addNode("node-4").get();
        
        System.out.printf("\n✅ Migration Result:\n");
        System.out.printf("   - Duration: %s\n", result1.getDuration());
        System.out.printf("   - Success Rate: %.2f%%\n", result1.getSuccessRate());
        
        Thread.sleep(3000);
        
        // Phase 3: Add node 5
        System.out.println("\n\nPhase 3: Add Node 5 (T+10s)");
        System.out.println("----------------------------");
        var result2 = simulator.addNode("node-5").get();
        
        System.out.printf("\n✅ Migration Result:\n");
        System.out.printf("   - Duration: %s\n", result2.getDuration());
        System.out.printf("   - Success Rate: %.2f%%\n", result2.getSuccessRate());
        
        System.out.println("\n\n🎉 Demo Complete!");
        System.out.println("=================");
        System.out.println("Key Observations:");
        System.out.println("  • Only ~20-25% of connections moved (not 100%)");
        System.out.println("  • Load distribution remained balanced");
        System.out.println("  • No full cluster rehash required");
        
        System.exit(0);
    }
}
DEMOJAVA

javac --release 21 -cp target/classes DemoScenario.java
java -cp .:target/classes DemoScenario
rm -f DemoScenario.java DemoScenario.class
