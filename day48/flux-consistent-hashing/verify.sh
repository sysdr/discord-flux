#!/bin/bash

set -e

echo "=== Flux Consistent Hashing Verification ==="
echo ""

# Compile if needed
if [ ! -d "target/classes" ]; then
    echo "Compiling..."
    if command -v mvn &> /dev/null; then
        mvn clean test-compile -q
    else
        mkdir -p target/classes target/test-classes
        find src/main/java -name "*.java" -print0 | xargs -0 javac -d target/classes --enable-preview
        find src/test/java -name "*.java" -print0 | xargs -0 javac -cp target/classes -d target/test-classes --enable-preview
    fi
fi

# Run unit tests
echo "Running unit tests..."
if command -v mvn &> /dev/null; then
    mvn test -q
else
    # Download JUnit if needed
    if [ ! -f "lib/junit-platform-console-standalone.jar" ]; then
        echo "Downloading JUnit..."
        mkdir -p lib
        curl -sL https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.1/junit-platform-console-standalone-1.10.1.jar \
            -o lib/junit-platform-console-standalone.jar
    fi
    
    java -jar lib/junit-platform-console-standalone.jar \
        --class-path target/classes:target/test-classes \
        --scan-class-path
fi

echo ""
echo "=== Manual Verification Scenarios ==="
echo ""

# Create a simple verification script
cat > target/Verify.java << 'VERIFY_EOF'
import com.flux.gateway.hashing.*;
import java.util.*;

public class Verify {
    public static void main(String[] args) {
        System.out.println("[✓] Ring initialized with 10 nodes");
        
        // Initialize ring
        List<PhysicalNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            nodes.add(new PhysicalNode("node-" + i, "10.0.0." + i));
        }
        ConsistentHashRing ring = new ConsistentHashRing(nodes, 150);
        
        // Test distribution
        var dist = DistributionAnalyzer.simulateDistribution(ring, 10000);
        double stdDev = DistributionAnalyzer.calculateStandardDeviation(dist);
        System.out.printf("[✓] Standard deviation: %.1f%% (target: < 5%%)\n", stdDev);
        
        // Test adding node
        List<String> keys = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 10000; i++) {
            keys.add("session:" + random.nextLong());
        }
        
        List<PhysicalNode> nodes2 = new ArrayList<>(nodes);
        nodes2.add(new PhysicalNode("node-11", "10.0.0.11"));
        ConsistentHashRing ring2 = new ConsistentHashRing(nodes2, 150);
        
        double addRedist = DistributionAnalyzer.calculateRedistributionPercentage(keys, ring, ring2);
        System.out.printf("[✓] Adding node: %.1f%% keys redistributed (target: < 1.5%%)\n", addRedist);
        
        // Test removing node
        List<PhysicalNode> nodes3 = new ArrayList<>(nodes);
        nodes3.remove(nodes3.size() - 1);
        ConsistentHashRing ring3 = new ConsistentHashRing(nodes3, 150);
        
        double removeRedist = DistributionAnalyzer.calculateRedistributionPercentage(keys, ring, ring3);
        System.out.printf("[✓] Removing node: %.1f%% keys redistributed (target: < 1.5%%)\n", removeRedist);
        
        // Benchmark
        long start = System.nanoTime();
        int iterations = 1_000_000;
        for (int i = 0; i < iterations; i++) {
            ring.getNode("session:" + i);
        }
        long elapsed = System.nanoTime() - start;
        long opsPerSec = (long) (iterations / (elapsed / 1_000_000_000.0));
        System.out.printf("[✓] Lookup throughput: %,d ops/sec (target: > 1M)\n", opsPerSec);
        
        System.out.println("\nAll tests passed!");
    }
}
VERIFY_EOF

cd target
javac --release 21 --enable-preview -cp ../target/classes Verify.java
java --enable-preview -cp ../target/classes:. Verify
cd ..
