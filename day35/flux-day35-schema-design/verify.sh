#!/bin/bash
cd "$(dirname "$0")"
echo "Verifying Partition Distribution"
if ! docker ps | grep -qE 'scylla|cassandra'; then
    echo "ScyllaDB/Cassandra container not running! Start with: docker run --name scylla -d -p 9042:9042 scylladb/scylla:latest (or cassandra:4)"
    exit 1
fi
echo "ScyllaDB is running"
CONTAINER=$(docker ps --format '{{.Names}}' | grep -E 'scylla|cassandra' | head -1)
docker exec "$CONTAINER" cqlsh -e "SELECT channel_id, bucket, message_count FROM flux.partition_metrics;" 2>/dev/null || echo "No data yet. Run ./demo.sh first."
echo "Dashboard: http://localhost:8080"
