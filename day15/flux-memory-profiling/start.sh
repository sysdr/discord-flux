#!/usr/bin/env bash
set -euo pipefail

echo "======================================"
echo "Starting Flux Memory Profiling Gateway"
echo "======================================"

# Compile
echo "[1/3] Compiling with Maven..."
mvn clean compile -q

# Check if JFR is available
echo "[2/3] Configuring JVM flags..."

JVM_FLAGS=(
    "-Xmx2g"
    "-Xms2g"
    "-XX:MaxDirectMemorySize=4g"
    "-XX:+HeapDumpOnOutOfMemoryError"
    "-XX:HeapDumpPath=/tmp"
    "-XX:+UseG1GC"
    "-XX:MaxGCPauseMillis=200"
    "-XX:+UnlockDiagnosticVMOptions"
    "-XX:StartFlightRecording=settings=profile,filename=/tmp/flux-profile.jfr"
)

# Start the gateway
echo "[3/3] Starting gateway..."
echo ""

mvn exec:java \
    -Dexec.mainClass="com.flux.gateway.LeakyGateway" \
    -Dexec.args="9000" \
    -Dexec.classpathScope=compile \
    -Dexec.vmArgs="${JVM_FLAGS[*]}"

