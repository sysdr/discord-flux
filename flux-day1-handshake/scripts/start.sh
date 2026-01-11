#!/bin/bash
cd "$(dirname "$0")/.."

echo "🔨 Compiling Flux Gateway..."
mvn clean compile

echo "🚀 Starting Flux Gateway..."
mvn exec:java -Dexec.mainClass="com.flux.gateway.FluxGateway" -Dexec.args="9001"
