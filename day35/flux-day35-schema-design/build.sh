#!/bin/bash
cd "$(dirname "$0")"
echo "Building Flux Day 35 project..."
mvn clean compile -q
echo "Build complete."
