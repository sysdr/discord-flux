#!/bin/bash
echo "🔧 Initializing Cassandra schema..."

# Wait for Cassandra to be ready
timeout 60 bash -c 'until docker exec cassandra cqlsh -e "DESCRIBE KEYSPACES" > /dev/null 2>&1; do sleep 2; done'

docker exec -i cassandra cqlsh << 'CQL'
CREATE KEYSPACE IF NOT EXISTS flux 
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE flux;

CREATE TABLE IF NOT EXISTS messages (
    channel_id bigint,
    message_id bigint,
    author_id bigint,
    content text,
    created_at timestamp,
    PRIMARY KEY (channel_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);

DESCRIBE TABLE messages;
CQL

echo "✅ Schema initialized"
