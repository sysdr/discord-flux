#!/bin/bash

echo "🧹 Cleaning up..."

# Kill Java processes
pkill -f FluxSerializationApp

# Remove build artifacts
mvn clean

# Remove generated protobuf
rm -rf src/main/java/com/flux/serialization/model/MessageProto.java

echo "✅ Cleanup complete!"
