#!/bin/bash
cd "$(dirname "$0")"
echo "Running 24-Hour Write Storm Demo"
mvn exec:java -Dexec.mainClass="com.flux.persistence.DemoRunner" -q
