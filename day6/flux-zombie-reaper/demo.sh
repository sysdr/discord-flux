#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

echo "🎬 Running Demo Scenario..."

mvn clean compile test-compile -q

echo ""
echo "📊 Scenario: 10,000 connections, 1,000 zombies"
echo "⏱️  This will take ~40 seconds..."
echo ""

mvn exec:java -Dexec.mainClass="com.flux.gateway.LoadTest" -q
