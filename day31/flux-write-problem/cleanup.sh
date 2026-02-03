#!/bin/bash

set -e

echo "🧹 Cleaning up Flux Day 31..."

pkill -f "com.flux.FluxApplication" 2>/dev/null || true
mvn clean -q 2>/dev/null || true
rm -rf target/

echo "✓ Cleanup complete"
