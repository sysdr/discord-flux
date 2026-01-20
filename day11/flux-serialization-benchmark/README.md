# Flux Day 11: Serialization Benchmark

## Quick Start
```bash
# 1. Setup project
bash scripts/start.sh

# 2. Open dashboard
open http://localhost:8080

# 3. Press ENTER in terminal to start benchmark

# 4. Verify results
bash scripts/verify.sh
```

## Project Structure
```
flux-serialization-benchmark/
├── src/
│   ├── main/
│   │   ├── java/com/flux/serialization/
│   │   │   ├── engine/      # Serialization engines
│   │   │   ├── benchmark/   # Benchmark runner
│   │   │   ├── model/       # Data models
│   │   │   ├── pool/        # Buffer pool
│   │   │   ├── metrics/     # Metrics collection
│   │   │   └── server/      # HTTP dashboard server
│   │   ├── proto/           # Protobuf schemas
│   │   └── resources/
│   └── test/
├── scripts/                 # Lifecycle scripts
└── pom.xml
```
