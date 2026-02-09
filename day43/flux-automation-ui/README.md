# Flux Automation UI & Workflow Engine

Production-grade workflow orchestration platform using Java 21 Virtual Threads.

## Quick Start
```bash
cd flux-automation-ui
./start.sh              # Start the server
./demo.sh               # Run demo scenarios
./verify.sh             # Verify implementation
./cleanup.sh            # Clean up
```

## Dashboard
http://localhost:8080/dashboard

## Features
- DAG-based workflow execution with automatic parallelization
- Virtual Threads for 100K+ concurrent workflows
- Persistent execution logs
- Real-time dashboard with metrics (Total, Completed, Failed, Active)
- Automatic retry with exponential backoff
