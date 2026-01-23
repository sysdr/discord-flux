#!/usr/bin/env bash
set -euo pipefail

echo "======================================"
echo "Flux Cleanup"
echo "======================================"

echo "Killing Java processes..."
pkill -f "com.flux.gateway" || true

echo "Removing compiled classes..."
mvn clean -q

echo "Removing heap dumps..."
rm -f /tmp/heap-*.hprof

echo "Removing JFR recordings..."
rm -f /tmp/flux-profile.jfr

echo ""
echo "✓ Cleanup complete"

