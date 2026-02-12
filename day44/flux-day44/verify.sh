#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://localhost:8085"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Flux Day 44 — Verification Suite"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check server is up
echo "→ [1/6] Health check (GET /api/metrics)..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/metrics")
if [ "$HTTP_CODE" = "200" ]; then
  echo "    ✓ Server is UP (HTTP 200)"
else
  echo "    ✗ Server is DOWN (HTTP $HTTP_CODE). Run ./start.sh first."
  exit 1
fi

# Fire a test ack
echo ""
echo "→ [2/6] Sending test ack for user=1, channel=1..."
RESULT=$(curl -s -X POST "$BASE_URL/api/ack" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"channelId":1,"messageId":9999999999999,"mentionDelta":0}')
echo "    Response: $RESULT"
echo "    ✓ Ack submitted"

# Fire a stale ack (should be STALE)
echo ""
echo "→ [3/6] Sending STALE ack (lower messageId, should be dropped)..."
STALE=$(curl -s -X POST "$BASE_URL/api/ack" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"channelId":1,"messageId":1000,"mentionDelta":0}')
echo "    Response: $STALE"
if echo "$STALE" | grep -q "STALE"; then
  echo "    ✓ Stale ack correctly dropped"
else
  echo "    ✗ Expected STALE result"
fi

# Trigger message burst + batch ack
echo ""
echo "→ [4/6] Triggering Message Burst + Batch Ack..."
curl -s -X POST "$BASE_URL/api/simulate?scenario=message_burst" > /dev/null
sleep 0.3
curl -s -X POST "$BASE_URL/api/simulate?scenario=batch_ack" > /dev/null
sleep 1.0

# Check metrics for coalescing
echo ""
echo "→ [5/6] Checking coalescing metrics..."
METRICS=$(curl -s "$BASE_URL/api/metrics")
TOTAL_ACKS=$(echo "$METRICS" | grep -o '"totalAcks":[0-9]*' | cut -d: -f2)
CASS_WRITES=$(echo "$METRICS" | grep -o '"cassandraWrites":[0-9]*' | cut -d: -f2)
DIRTY=$(echo "$METRICS" | grep -o '"dirtyQueueDepth":[0-9]*' | cut -d: -f2)
echo "    Total Acks     : $TOTAL_ACKS"
echo "    Cassandra Writes: $CASS_WRITES"
echo "    Dirty Queue     : $DIRTY"
if [ "${TOTAL_ACKS:-0}" -gt "${CASS_WRITES:-1}" ]; then
  echo "    ✓ Write coalescing confirmed (acks > cassandraWrites)"
else
  echo "    ✗ Coalescing not yet visible — try after first 5s flush cycle"
fi

# Force flush
echo ""
echo "→ [6/6] Triggering force flush and checking dirty queue drains..."
curl -s -X POST "$BASE_URL/api/simulate?scenario=force_flush" > /dev/null
sleep 1.5
METRICS2=$(curl -s "$BASE_URL/api/metrics")
DIRTY2=$(echo "$METRICS2" | grep -o '"dirtyQueueDepth":[0-9]*' | cut -d: -f2)
echo "    Dirty Queue After Flush: ${DIRTY2:-unknown}"
if [ "${DIRTY2:-99}" -lt 10 ]; then
  echo "    ✓ Dirty queue drained successfully"
else
  echo "    ✗ Dirty queue still has entries — Cassandra may be slow"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Verification complete."
echo "  Open http://localhost:8085/dashboard for live view."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
