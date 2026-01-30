#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Running Verification Tests ==="
echo ""

# Run unit tests
echo "📋 Running unit tests..."
mvn test -q

if [ $? -eq 0 ]; then
    echo "✅ All tests passed!"
else
    echo "❌ Tests failed"
    exit 1
fi

echo ""
echo "🔍 Verification complete!"
echo ""
echo "Key validations:"
echo "  ✓ Ring buffer write/read operations"
echo "  ✓ Buffer full detection (backpressure)"
echo "  ✓ Utilization percentage calculation"
echo "  ✓ Concurrent access safety"
echo "  ✓ No message loss under load"
