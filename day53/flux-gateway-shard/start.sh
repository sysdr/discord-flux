#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "======================================================"
echo " Flux Gateway — Day 53 | start.sh"
echo "======================================================"

# Check Java 21+
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ -z "$JAVA_VER" ] || [ "$JAVA_VER" -lt 21 ]; then
  echo "ERROR: Java 21+ required. Found: $(java -version 2>&1 | head -1)"
  exit 1
fi
echo "[OK] Java $JAVA_VER detected."

# Compile
echo "[BUILD] Running mvn package..."
mvn -q package -DskipTests

JAR="target/flux-gateway-shard-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
  echo "ERROR: JAR not found after build. Check mvn output above."
  exit 1
fi
echo "[OK] Build succeeded: $JAR"

# JVM flags: ZGC for low-pause, DirectMemory cap, virtual thread diagnostics
JVM_FLAGS=(
  "-Xmx512m"
  "-XX:MaxDirectMemorySize=128m"
  "-XX:+UseZGC"
  "-XX:+ZGenerational"
  "-Xlog:gc*:file=gc.log:tags,time,uptime:filecount=3,filesize=5m"
  "--enable-preview"
  "-Djdk.tracePinnedThreads=short"
)

echo "[START] Launching Gateway + Dashboard..."
echo "        WebSocket : ws://localhost:8888"
echo "        Dashboard : http://localhost:8080"
echo "        GC log    : gc.log"
echo ""
echo "        Press Ctrl+C to stop."
echo ""

exec java "${JVM_FLAGS[@]}" -jar "$JAR"
