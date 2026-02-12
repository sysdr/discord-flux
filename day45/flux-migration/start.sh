#!/bin/bash
set -e
cd "$(dirname "$0")"

echo "🔨 Compiling Flux Migration..."
mvn clean compile -q

DASHBOARD_PORT=8081
echo "🚀 Starting Dashboard..."
echo ""
echo "📊 Dashboard link: http://localhost:${DASHBOARD_PORT}/dashboard"
echo ""

# Start dashboard in background (include runtime deps for SLF4J, etc.)
CP="target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)"
java -cp "$CP" com.flux.migration.DashboardServer "$DASHBOARD_PORT" &
DASHBOARD_PID=$!

echo "Dashboard PID: $DASHBOARD_PID" > .pids

# Wait for user to stop
echo "Press Ctrl+C to stop..."
wait $DASHBOARD_PID
