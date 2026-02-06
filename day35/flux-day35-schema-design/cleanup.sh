#!/bin/bash
# Project-level cleanup - stops app and Maven clean
# For full Docker/artifact cleanup, use ../cleanup.sh
cd "$(dirname "$0")"
pkill -f "FluxPersistenceApp" 2>/dev/null || true
pkill -f "exec:java.*com.flux.persistence" 2>/dev/null || true
mvn clean -q 2>/dev/null || true
echo "Cleanup complete"
