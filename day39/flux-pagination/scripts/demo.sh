#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}/.."

echo "📝 Running load test demo..."

mvn clean compile exec:java -Dexec.mainClass="com.flux.LoadTestDemo" -Dexec.args="1 1000" -q
