# Flux Consistent Hashing Demo

Production-grade implementation of Consistent Hashing for distributed gateway clusters.

## Quick Start

```bash
# Start the demo with live dashboard
./start.sh

# Open http://localhost:8080/dashboard in your browser
```

## Scripts

- `start.sh` - Compile and run the demo with dashboard
- `demo.sh` - Run a production scaling scenario
- `verify.sh` - Run all tests and benchmarks
- `cleanup.sh` - Remove compiled files and stop processes

## Architecture

This implementation demonstrates:
- Virtual nodes for uniform distribution (150 per physical node)
- O(log n) lookup using TreeMap
- VarHandle for lock-free ring updates
- MurmurHash3 for high-quality key distribution
- Zero-allocation hot path

## Performance Targets

- Standard Deviation: < 5% (with 150 virtual nodes)
- Redistribution: < 1.5% per node add/remove
- Lookup Throughput: > 1M ops/sec
- Memory Overhead: ~1MB per 100 nodes

## Homework

Implement weighted consistent hashing to handle heterogeneous node capacities.
See lesson_article.md for details.
