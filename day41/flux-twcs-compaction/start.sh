#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "🔨 Compiling project..."
mvn -q clean compile

echo "🚀 Starting Flux Storage Engine..."
mvn -q exec:java -Dexec.mainClass="com.flux.FluxStorageEngine"
