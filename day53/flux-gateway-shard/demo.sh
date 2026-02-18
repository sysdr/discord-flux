#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-normal}"
LOADGEN_CP="target/flux-gateway-shard-1.0.0-SNAPSHOT.jar:target/test-classes"

# Build test classes if needed
if [ ! -d "target/test-classes" ]; then
  mvn -q test-compile --enable-preview 2>/dev/null || mvn test-compile
fi

echo "======================================================"
echo " Flux Gateway — Day 53 | demo.sh [mode=$MODE]"
echo "======================================================"
echo ""

case "$MODE" in
  normal)
    echo "[DEMO] Simulating 16-shard bot fleet connecting..."
    java --enable-preview -cp "$LOADGEN_CP" com.flux.gateway.load.ShardLoadGenerator 16 normal
    ;;
  zombie)
    echo "[DEMO] Zombie shard eviction scenario:"
    echo "       Step 1 — Connect all 16 shards"
    echo "       Step 2 — Disconnect shard [3,16] via OS close"
    echo "       Step 3 — Reconnect shard [3,16] — triggers zombie eviction"
    java --enable-preview -cp "$LOADGEN_CP" com.flux.gateway.load.ShardLoadGenerator 16 zombie
    ;;
  conflict)
    echo "[DEMO] Shard conflict scenario:"
    echo "       Two bots both claim shard [5,16] simultaneously."
    echo "       First one wins. Second gets INVALID_SESSION (op=9)."
    java --enable-preview -cp "$LOADGEN_CP" com.flux.gateway.load.ShardLoadGenerator 16 conflict
    ;;
  load)
    THREADS="${2:-100}"
    echo "[DEMO] Load test: $THREADS concurrent virtual threads sending IDENTIFY..."
    java --enable-preview -cp "$LOADGEN_CP" com.flux.gateway.load.ShardLoadGenerator "$THREADS" normal
    ;;
  *)
    echo "Usage: ./demo.sh [normal|zombie|conflict|load <N>]"
    exit 1
    ;;
esac
