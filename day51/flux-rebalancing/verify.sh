#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "✅ Running Verification Tests..."
echo "================================"
echo ""

# Compile main first
javac --release 21 \
    -d target/classes \
    -sourcepath src/main/java \
    src/main/java/com/flux/rebalancing/*.java 2>/dev/null

if [ $? -ne 0 ]; then
    echo "❌ Main compilation failed"
    exit 1
fi

echo "🔍 Verifying Implementation..."
echo ""

# Create verification script
cat > Verify.java << 'VERIFYJAVA'
import com.flux.rebalancing.*;
import java.util.*;

public class Verify {
    public static void main(String[] args) {
        boolean allPassed = true;
        
        System.out.println("Test 1: Ring Initialization");
        System.out.println("---------------------------");
        var ring = new ConsistentHashRing(150);
        ring.addNode(GatewayNode.create("node-1", "10.0.0.1", 9001));
        ring.addNode(GatewayNode.create("node-2", "10.0.0.2", 9002));
        ring.addNode(GatewayNode.create("node-3", "10.0.0.3", 9003));
        
        if (ring.getPhysicalNodeCount() == 3 && ring.getRingSize() == 450) {
            System.out.println("✅ PASS: Ring properly initialized with virtual nodes");
        } else {
            System.out.println("❌ FAIL: Ring initialization incorrect");
            allPassed = false;
        }
        
        System.out.println("\nTest 2: Load Distribution Quality");
        System.out.println("-----------------------------------");
        Map<String, GatewayNode> connections = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            String connId = "conn-" + i;
            connections.put(connId, ring.getNodeForConnection(connId));
        }
        
        double variance = ring.calculateLoadVariance(connections);
        System.out.printf("   Coefficient of Variation: %.4f\n", variance);
        
        if (variance < 0.10) {
            System.out.println("✅ PASS: Load distribution is balanced (CV < 0.10)");
        } else {
            System.out.println("❌ FAIL: Load distribution variance too high");
            allPassed = false;
        }
        
        System.out.println("\nTest 3: Minimal Key Movement");
        System.out.println("-----------------------------");
        Map<String, String> originalMapping = new HashMap<>();
        for (var entry : connections.entrySet()) {
            originalMapping.put(entry.getKey(), entry.getValue().nodeId());
        }
        
        ring.addNode(GatewayNode.create("node-4", "10.0.0.4", 9004));
        
        int moved = 0;
        for (var entry : originalMapping.entrySet()) {
            String currentNode = ring.getNodeForConnection(entry.getKey()).nodeId();
            if (!entry.getValue().equals(currentNode)) {
                moved++;
            }
        }
        
        double movementRatio = (moved / (double) connections.size()) * 100;
        double theoretical = 25.0; // 1/4 = 25%
        System.out.printf("   Moved: %.2f%% (theoretical: %.2f%%)\n", movementRatio, theoretical);
        
        if (movementRatio <= theoretical + 5.0) {
            System.out.println("✅ PASS: Key movement within acceptable range");
        } else {
            System.out.println("❌ FAIL: Too many keys moved");
            allPassed = false;
        }
        
        System.out.println("\n" + "=".repeat(50));
        if (allPassed) {
            System.out.println("✅ ALL TESTS PASSED");
            System.exit(0);
        } else {
            System.out.println("❌ SOME TESTS FAILED");
            System.exit(1);
        }
    }
}
VERIFYJAVA

javac --release 21 -cp target/classes Verify.java
java -cp .:target/classes Verify
RESULT=$?
rm -f Verify.java Verify.class
exit $RESULT
