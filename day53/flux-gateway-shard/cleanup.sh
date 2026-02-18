#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
echo "[Cleanup] Stopping Flux Gateway processes..."

# Kill any java process running our jar
pkill -f "flux-gateway-shard" 2>/dev/null && echo "[OK] Gateway stopped." || \
  echo "[INFO] No running Gateway process found."

# Remove build artifacts
[ -d target ] && { rm -rf target; echo "[OK] target/ removed."; }

# Remove logs
rm -f gc.log gc.log.* *.log 2>/dev/null
echo "[OK] Logs removed."
echo "[Cleanup] Done."
