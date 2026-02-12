#!/bin/bash
set -e
cd "$(dirname "$0")"

echo "🔍 Verifying Migration..."

# Check if Cassandra is running
if ! docker ps | grep -q scylla; then
    echo "❌ ScyllaDB container not running. Start with:"
    echo "   docker run -d --name scylla -p 9042:9042 scylladb/scylla"
    exit 1
fi

# Query Cassandra
echo "📊 Querying Cassandra for migrated messages..."
docker exec scylla cqlsh -e "SELECT COUNT(*) FROM flux.messages;" | grep -A 1 "count" || {
    echo "⚠️  Table might be empty or keyspace not created"
    echo "Run demo.sh first to migrate sample data"
}

# Check checkpoint file
if [ -f migration.checkpoint ]; then
    echo ""
    echo "📝 Checkpoint file contents:"
    cat migration.checkpoint
else
    echo "⚠️  No checkpoint file found"
fi

# Check heap usage
echo ""
echo "💾 JVM Memory Stats:"
jps | grep MigrationOrchestrator | awk '{print $1}' | xargs -I {} jstat -gc {} 2>/dev/null || echo "No running migration process"

echo ""
echo "✅ Verification complete"
