#!/bin/bash

set -e

cd "$(dirname "$0")"

PROJECT_DIR="flux-gateway"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "❌ Project directory not found: $PROJECT_DIR"
    echo "   Run ./setup.sh first to generate the project"
    exit 1
fi

cd "$PROJECT_DIR"

echo "🔨 Building Flux Gateway..."
echo "============================"
echo ""

# Check for Maven
if command -v mvn &> /dev/null; then
    echo "📦 Using Maven..."
    echo ""
    
    echo "🧹 Cleaning previous build..."
    mvn clean -q
    
    echo "🔨 Compiling main sources..."
    mvn compile -q
    
    echo "🧪 Compiling test sources..."
    mvn test-compile -q
    
    echo "✅ Running tests..."
    mvn test
    
    echo ""
    echo "✅ Build complete!"
    echo ""
    echo "Next steps:"
    echo "  ./start.sh    - Start the gateway"
    echo "  ./demo.sh     - Run demo scenario"
    echo "  ./verify.sh   - Verify installation"
else
    echo "📦 Using javac (Maven not found)..."
    echo ""
    
    # Create target directories
    mkdir -p target/classes target/test-classes
    
    echo "🔨 Compiling main sources..."
    find src/main/java -name "*.java" > /tmp/sources.txt 2>/dev/null || true
    if [ -s /tmp/sources.txt ]; then
        javac --enable-preview --source 21 -d target/classes @/tmp/sources.txt
        rm /tmp/sources.txt
        echo "✅ Main sources compiled"
    else
        echo "❌ No source files found"
        exit 1
    fi
    
    echo "🧪 Compiling test sources..."
    find src/test/java -name "*.java" > /tmp/test-sources.txt 2>/dev/null || true
    if [ -s /tmp/test-sources.txt ]; then
        javac --enable-preview --source 21 -cp "target/classes:target/test-classes" -d target/test-classes @/tmp/test-sources.txt
        rm /tmp/test-sources.txt
        echo "✅ Test sources compiled"
    else
        echo "⚠️  No test files found"
    fi
    
    echo ""
    echo "✅ Build complete!"
    echo ""
    echo "Note: Tests require Maven to run. Install Maven for full test execution."
    echo ""
    echo "Next steps:"
    echo "  ./start.sh    - Start the gateway"
    echo "  ./demo.sh     - Run demo scenario"
fi
