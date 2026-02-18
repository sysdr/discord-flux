#!/usr/bin/env bash
# Verifies the Day 53 scenario from the command line without a browser.
set -euo pipefail
cd "$(dirname "$0")"

METRICS_URL="http://localhost:8080/metrics"
SHARDS_URL="http://localhost:8080/shards"
PASS=0
FAIL=0

check() {
  local label="$1" condition="$2"
  if eval "$condition" > /dev/null 2>&1; then
    echo "  [PASS] $label"
    PASS=$((PASS+1))
  else
    echo "  [FAIL] $label"
    FAIL=$((FAIL+1))
  fi
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "ERROR: '$1' not found."; exit 1; }
}
require_command curl
require_command jq

echo "======================================================"
echo " Flux Gateway — Day 53 | verify.sh"
echo "======================================================"

# ── Gateway reachability ──────────────────────────────────────────────────
echo ""
echo "[1] Dashboard reachability..."
check "HTTP 200 from /metrics" "curl -sf '$METRICS_URL' > /dev/null"
check "HTTP 200 from /shards"  "curl -sf '$SHARDS_URL' > /dev/null"

# ── Run demo and capture state ────────────────────────────────────────────
echo ""
echo "[2] Running normal demo (16 shards)..."
./demo.sh normal 2>&1 | tail -5
sleep 1

echo ""
echo "[3] Verifying shard state via /shards..."
SHARDS_JSON=$(curl -sf "$SHARDS_URL")
ACTIVE_COUNT=$(echo "$SHARDS_JSON" | jq 'length')
echo "    Active shards reported: $ACTIVE_COUNT"
# Due to fast connections, count may be 0 after demo completes — check metrics instead
METRICS_JSON=$(curl -sf "$METRICS_URL")
IDENTIFY_OK=$(echo "$METRICS_JSON" | jq '.identifySuccess')
echo "    Total successful IDENTIFYs: $IDENTIFY_OK"
check "At least 16 successful IDENTIFYs" "[ '$IDENTIFY_OK' -ge 16 ]"

# ── Conflict scenario ────────────────────────────────────────────────────
echo ""
echo "[4] Running conflict demo (duplicate shard [5,16])..."
./demo.sh conflict 2>&1 | tail -4
sleep 0.5

METRICS_JSON=$(curl -sf "$METRICS_URL")
REJECTED=$(echo "$METRICS_JSON" | jq '.identifyRejected')
echo "    Rejected (conflict) count: $REJECTED"
check "At least 1 shard conflict rejected" "[ '$REJECTED' -ge 1 ]"

# ── Heartbeat verification ────────────────────────────────────────────────
echo ""
echo "[5] Checking heartbeat ACKs..."
METRICS_JSON=$(curl -sf "$METRICS_URL")
HB=$(echo "$METRICS_JSON" | jq '.heartbeats')
echo "    Heartbeats processed: $HB"
check "At least 1 heartbeat processed" "[ '$HB' -ge 1 ]"

# ── Parse errors should be zero ───────────────────────────────────────────
echo ""
echo "[6] Checking for parse errors..."
PARSE_ERRS=$(echo "$METRICS_JSON" | jq '.identifyParseErrors')
check "Zero parse errors in clean run" "[ '$PARSE_ERRS' -eq 0 ]"

# ── Unit tests ────────────────────────────────────────────────────────────
echo ""
echo "[7] Running unit tests..."
mvn -q test 2>/dev/null && check "All JUnit tests pass" "true" || check "All JUnit tests pass" "false"

# ── Summary ───────────────────────────────────────────────────────────────
echo ""
echo "======================================================"
echo " Results: $PASS PASSED  $FAIL FAILED"
echo "======================================================"
[ "$FAIL" -eq 0 ] && echo " ✓ All checks passed. Day 53 scenario verified." || \
                     echo " ✗ Some checks failed. See output above."
