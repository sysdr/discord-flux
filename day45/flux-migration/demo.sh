#!/bin/bash
set -e
cd "$(dirname "$0")"

echo "🎬 Running Migration Demo Scenario..."
echo ""
echo "Scenario: Migrate 5 messages from sample_data.json"
echo "Expected: All messages written to Cassandra with checkpoint tracking"
echo ""

# Compile if needed
mvn compile -q

echo "▶️  Starting migration..."
java -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout) \
    com.flux.migration.MigrationOrchestrator sample_data.json

echo ""
echo "✅ Demo completed. Check Cassandra:"
echo "   docker exec -it scylla cqlsh"
echo "   SELECT * FROM flux.messages;"
