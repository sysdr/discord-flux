#!/bin/bash

echo "📦 Building Flux Day 30 Integration Test..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "✅ Build successful"
else
    echo "❌ Build failed"
    exit 1
fi
