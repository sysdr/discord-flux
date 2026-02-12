#!/bin/bash
set -e
cd "$(dirname "$0")"
echo "=== Tests ==="
mvn test -q && echo "Tests passed."
echo "=== Duplicate services ==="
pgrep -af "DashboardServer|MigrationOrchestrator" 2>/dev/null || echo "None"
echo "=== Metrics (dashboard on 8080) ==="
curl -s http://localhost:8080/metrics 2>/dev/null || echo "Start: bash start.sh"
[ -f metrics.json ] && echo "metrics.json present" || echo "Run demo.sh for metrics"
echo "✅ Done. demo.sh then http://localhost:8080/dashboard for non-zero values"
