# Flux Day 18: Topic Design - Guild vs User Routing

## Overview
This project demonstrates why guild-centric topic routing is superior to user-centric routing for group chat systems at scale.

## Quick Start

1. **Start the broker + dashboard:**
```bash
   bash start.sh
```
   Opens dashboard at http://localhost:8080/

2. **Run load test comparison:**
```bash
   bash demo.sh
```

3. **Verify correctness:**
```bash
   bash verify.sh
```

4. **Cleanup:**
```bash
   bash cleanup.sh
```

## Key Concepts

- **User-Centric**: Each user has a topic. Messages published N times (one per recipient).
- **Guild-Centric**: Each guild has a topic. Messages published once, routed to all members.
- **Ring Buffer**: Bounded queue per subscriber to handle slow consumers.

## Project Structure
```
src/main/java/com/flux/pubsub/
├── Subscriber.java              # Subscriber interface
├── BoundedRingBuffer.java       # Lock-free bounded queue
├── GatewaySubscriber.java       # WebSocket subscriber implementation
├── LocalPubSubBroker.java       # In-memory pub/sub broker
├── TopologyComparison.java      # Benchmarking framework
├── DashboardServer.java         # Real-time visualization
├── PubSubTopologyDemo.java      # Main entry point
└── LoadTest.java                # Concurrent load test
```

## Metrics

Watch for:
- **Topics**: Should equal guild count (not user count)
- **Publications/sec**: Should match message rate 1:1
- **Drops**: Should be near zero unless intentionally overloading

## Homework

Extend the broker to support distributed pub/sub across multiple JVMs using a cluster bridge.
