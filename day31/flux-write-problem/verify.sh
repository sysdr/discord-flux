#!/bin/bash

set -e

echo "🔍 Verifying Flux Day 31 Implementation"
echo "========================================"

echo "Running unit tests..."
mvn test -q
echo "✓ All tests passed"

echo ""
echo "Checking Snowflake ID generator..."
cat <<'JAVA' > VerifySnowflake.java
import com.flux.core.SnowflakeGenerator;

public final class VerifySnowflake {
    public static void main(String[] args) throws Exception {
        var generator = new SnowflakeGenerator(1, 1);
        long id1 = generator.nextId();
        long id2 = generator.nextId();
        if (id2 <= id1) {
            throw new IllegalStateException("IDs not monotonic");
        }
        var ids = new java.util.HashSet<Long>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(generator.nextId());
        }
        if (ids.size() != 100_000) {
            throw new IllegalStateException("Collision detected");
        }
        id1 = generator.nextId();
        Thread.sleep(2);
        id2 = generator.nextId();
        if (SnowflakeGenerator.extractTimestamp(id2) < SnowflakeGenerator.extractTimestamp(id1)) {
            throw new IllegalStateException("Timestamps not ordered");
        }
        System.out.println("✓ Snowflake IDs validated");
    }
}
JAVA

CLASSPATH=$(mvn dependency:build-classpath -q -DincludeScope=runtime | tail -1)
javac -cp target/classes:"$CLASSPATH" VerifySnowflake.java
java -cp target/classes:"$CLASSPATH":. VerifySnowflake
rm VerifySnowflake.java VerifySnowflake.class

echo ""
echo "✓ Verification complete"
