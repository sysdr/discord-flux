#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎯 Flux Shard Router - Demo Scenario"
echo "======================================"

# Compile project
mvn compile -q

# Run load generation demo
cat > /tmp/DemoRunner.java << 'DEMO'
import com.flux.shard.generator.LoadGenerator;
import com.flux.shard.gateway.ShardDistributionTracker;

public class DemoRunner {
    public static void main(String[] args) {
        System.out.println("\n🔥 Demo: Realistic Guild Load Simulation\n");
        
        int totalShards = 64;
        ShardDistributionTracker tracker = new ShardDistributionTracker(totalShards);
        LoadGenerator generator = new LoadGenerator(tracker, totalShards);
        
        // Scenario: 1000 guilds, each generating 100 events
        System.out.println("Simulating 1000 guilds with realistic Snowflake IDs...");
        generator.generateRealisticLoad(1000, 100);
        
        System.out.println("\n📊 Distribution Analysis:");
        var stats = tracker.getStats();
        System.out.printf("  Mean: %.2f events/shard\n", stats.mean());
        System.out.printf("  Std Dev: %.2f (CV: %.1f%%)\n", 
            stats.stdDev(), stats.coefficientOfVariation());
        System.out.printf("  Range: %d - %d\n", stats.min(), stats.max());
        System.out.printf("  Max Deviation: %.2fx mean\n", stats.maxDeviation());
        
        if (stats.coefficientOfVariation() < 20.0) {
            System.out.println("\n✅ PASS: Distribution is balanced (CV < 20%)");
        } else {
            System.out.println("\n⚠️ WARNING: Distribution shows imbalance");
        }
        
        System.out.println("\nVisualize: http://localhost:8080\n");
    }
}
DEMO

# Compile and run demo
javac -cp target/classes -d target/classes /tmp/DemoRunner.java
java -cp target/classes DemoRunner

rm /tmp/DemoRunner.java

echo ""
echo "🎬 Demo complete!"
echo "💡 Open http://localhost:8080 to see the live dashboard"
