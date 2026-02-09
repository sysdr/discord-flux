# Flux gRPC Service

Production-grade gRPC data service layer for ScyllaDB.

## Quick Start

```bash
# 1. Start ScyllaDB (Docker)
docker run -d -p 9042:9042 --name scylla scylladb/scylla:latest --smp 1
docker exec scylla cqlsh -e "CREATE KEYSPACE flux_messages WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};"
docker exec scylla cqlsh -e "CREATE TABLE flux_messages.messages (channel_id bigint, message_id bigint, author_id bigint, content text, timestamp bigint, PRIMARY KEY (channel_id, message_id)) WITH CLUSTERING ORDER BY (message_id DESC);"

# 2. Start gRPC server
./start.sh

# 3. Run demo (in another terminal)
./demo.sh

# 4. Verify
./verify.sh
```

## Architecture

- **gRPC Server**: Port 9090 (with Server Reflection)
- **Metrics Dashboard**: http://localhost:8080
- **Transport**: HTTP/2 with Virtual Threads
- **Database**: ScyllaDB on localhost:9042

## Testing

```bash
# List services
grpcurl -plaintext localhost:9090 list

# Insert message
grpcurl -plaintext -d '{"channel_id": 123, "message_id": 1234567890, "author_id": 999, "content": "Hello"}' \
  localhost:9090 flux.MessageService/InsertMessage

# Stream history
grpcurl -plaintext -d '{"channel_id": 123, "limit": 10}' \
  localhost:9090 flux.MessageService/StreamMessageHistory
```

## Cleanup

```bash
./cleanup.sh
docker stop scylla && docker rm scylla
```
