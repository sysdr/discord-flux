#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

PROJECT_DIR="flux-zombie-reaper"

echo "🔨 Building Flux Gateway - Zombie Reaper..."

# Check if project directory exists
if [ ! -d "$PROJECT_DIR" ]; then
    echo "❌ Project directory '$PROJECT_DIR' not found. Running setup.sh first..."
    bash setup.sh
    if [ $? -ne 0 ]; then
        echo "❌ Setup failed"
        exit 1
    fi
fi

cd "$PROJECT_DIR" || exit 1

echo "🧹 Cleaning previous build..."
mvn clean -q

echo "🔨 Compiling..."
mvn compile -q

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"

echo "🧪 Running tests..."
mvn test -q

if [ $? -ne 0 ]; then
    echo "❌ Tests failed"
    exit 1
fi

echo "✅ All tests passed"

echo "📦 Packaging..."
mvn package -q -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Packaging failed"
    exit 1
fi

echo ""
echo "✅ Build complete!"
echo "📦 Artifact: $PROJECT_DIR/target/zombie-reaper-1.0-SNAPSHOT.jar"
echo ""
echo "🚀 To start the server:"
echo "   cd $PROJECT_DIR"
echo "   ./start.sh"
echo ""
