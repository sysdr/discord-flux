#!/bin/bash
cd "$(dirname "$0")"
echo "🔨 Building Flux Consistency Simulator..."
mvn -q clean compile test
echo "✅ Build complete"
