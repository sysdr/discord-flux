#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Verifying ScyllaDB State"

echo ""
echo "Total message count:"
docker exec scylla sh -c 'cqlsh $(hostname -i) -e "SELECT COUNT(*) FROM flux.messages;"' 2>/dev/null || echo "Database not ready"

echo ""
echo "Table statistics:"
docker exec scylla nodetool tablestats flux.messages 2>/dev/null || echo "Nodetool unavailable"

echo ""
echo "Sample messages from channel 1:"
docker exec scylla sh -c 'cqlsh $(hostname -i) -e "SELECT channel_id, message_id, user_id, content FROM flux.messages WHERE channel_id = 1 LIMIT 5;"' 2>/dev/null || echo "Query failed"

echo ""
echo "Partition distribution (first 10 channels):"
for i in {1..10}; do
    count=$(docker exec scylla sh -c "cqlsh \$(hostname -i) -e \"SELECT COUNT(*) FROM flux.messages WHERE channel_id = $i;\"" 2>/dev/null | awk '/^[[:space:]]*[0-9]+[[:space:]]*$/{print $1; exit}' || echo "0")
    echo "  Channel $i: $count messages"
done

echo ""
echo "Verification complete"
