#!/bin/bash
cd "$(dirname "$0")"
echo "🔨 Compiling Flux Consistency Simulator..."
mvn -q clean compile

echo "🚀 Starting server..."
mvn -q exec:java -Dexec.mainClass="com.flux.persistence.SimulatorServer"
