# Flux Day 35: Cassandra Schema Design

## Quick Start
1. docker run --name scylla -d -p 9042:9042 scylladb/scylla:latest
2. ./start.sh  (in one terminal)
3. ./demo.sh   (in another terminal)
4. Open http://localhost:8080

## Cleanup
./cleanup.sh
docker stop scylla && docker rm scylla
