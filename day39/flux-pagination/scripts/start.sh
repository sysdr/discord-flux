#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}/.."

echo "🔨 Compiling project..."
mvn clean compile -q

echo "🚀 Starting Flux Pagination Server..."
mvn exec:java -Dexec.mainClass="com.flux.FluxPaginationServer" -q
