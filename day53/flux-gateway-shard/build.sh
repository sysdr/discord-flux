#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "======================================================"
echo " Flux Gateway — Day 53 | build.sh"
echo "======================================================"

# Check Java 21+
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ -z "$JAVA_VER" ] || [ "$JAVA_VER" -lt 21 ]; then
  echo "ERROR: Java 21+ required. Found: $(java -version 2>&1 | head -1)"
  exit 1
fi
echo "[OK] Java $JAVA_VER detected."

echo "[BUILD] Running mvn package (with tests)..."
mvn -q package

JAR="target/flux-gateway-shard-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
  echo "ERROR: JAR not found after build."
  exit 1
fi
echo "[OK] Build succeeded: $JAR"
echo "======================================================"
