#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

TYPERS=${1:-100}
DURATION=${2:-10}

echo "🎬 Running load test: $TYPERS typers for $DURATION seconds"
echo ""

MAVEN_OPTS="--enable-preview" mvn exec:java -Dexec.mainClass="com.flux.gateway.LoadTest" -Dexec.args="$TYPERS $DURATION"
