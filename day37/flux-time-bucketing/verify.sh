#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔍 VERIFICATION MODE"
echo "===================="
echo

mvn clean test -q
if [ $? -eq 0 ]; then
    echo "✅ All unit tests passed"
else
    echo "❌ Unit tests failed"
    exit 1
fi

echo
echo "Running bucket validation..."
mvn exec:java -Dexec.mainClass="com.flux.Validator" -q

echo
echo "✅ Verification complete"
