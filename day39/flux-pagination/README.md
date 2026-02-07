# Flux Pagination - Cursor-Based Pagination with Cassandra

## Quick Start

### Prerequisites
- Java 21+
- Docker (for Cassandra)
- Maven 3.9+

### Setup

1. **Start Cassandra**:
```bash
docker run -d --name cassandra -p 9042:9042 cassandra:4.1
```

2. **Initialize Schema**:
```bash
./scripts/init_schema.sh
```

3. **Load Test Data**:
```bash
./scripts/demo.sh
```

4. **Start Server**:
```bash
./scripts/start.sh
```

5. **Open Dashboard**:
Navigate to http://localhost:8080/dashboard.html

### Verification
```bash
./scripts/verify.sh
```

### Cleanup
```bash
./scripts/cleanup.sh
docker stop cassandra && docker rm cassandra
```

## Architecture

This implementation demonstrates production-grade cursor-based pagination:
- **Stateless Cursors**: Client manages pagination state
- **Zero Materialization**: ResultSet streaming prevents heap exhaustion
- **Snowflake IDs**: Time-sortable 64-bit identifiers
- **Virtual Threads**: Handle concurrent requests without pool exhaustion

## API Endpoints

- `GET /messages?channel_id={id}&cursor={cursor}&limit={size}` - Fetch page
- `GET /stats` - Query metrics
- `POST /insert` - Insert test message

## Testing

Run unit tests:
```bash
mvn test
```

## Performance Monitoring

1. Open VisualVM
2. Connect to `FluxPaginationServer` process
3. Monitor heap usage during pagination
4. Young GC frequency should remain < 5/min

## Key Metrics

- **Query Latency**: < 50ms per page (50 messages)
- **Heap Allocation**: < 200 MB under 1000 concurrent users
- **GC Frequency**: < 10 Young GC per minute
