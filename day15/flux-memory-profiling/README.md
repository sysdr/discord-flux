# Flux Day 15: Memory Profiling

## Quick Start
```bash
# 1. Start the gateway
./start.sh

# 2. Open dashboard
open http://localhost:8080/dashboard

# 3. Run load test (in another terminal)
./demo.sh

# 4. Verify results
./verify.sh

# 5. Clean up
./cleanup.sh
```

## Expected Behavior

After running the demo for 5 minutes:

- **Heap**: Grows from 200MB → 1.5GB (should stay flat!)
- **Session Map**: Contains 40k+ entries (only 2k should be active)
- **DirectBuffer**: 1.2GB off-heap (excessive pooling)
- **Leak Detector**: Reports thousands of leaked sentinels

## Analysis

Heap dumps will be in `/tmp/heap-*.hprof`.

Analyze with Eclipse MAT:
```bash
mat.sh /tmp/heap-*.hprof
```

Look for:
- LeakyGateway → sessions ConcurrentHashMap (40k+ entries)
- BufferPool → pool ConcurrentLinkedQueue (18k+ DirectByteBuffers)
- Thread → ThreadLocalMap (96+ entries per carrier thread)
- GlobalListenerRegistry → listeners ArrayList (5k+ closures)
