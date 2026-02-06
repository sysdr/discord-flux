#!/bin/bash
cd "$(dirname "$0")"
echo "Starting Flux Day 35: Schema Design"
mvn clean compile -q
echo "Initializing Cassandra schema..."
mvn exec:java -Dexec.mainClass="com.flux.persistence.SchemaInitializer" -q
echo "Starting dashboard server..."
mvn exec:java -Dexec.mainClass="com.flux.persistence.FluxPersistenceApp"
