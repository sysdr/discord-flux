#!/bin/bash
set -e

echo "🌱 Seeding Redis with test guild data..."

# Clear existing data
redis-cli del guild:test-guild:members > /dev/null

# Generate 100,000 test members
echo "Generating 100,000 members (this may take a minute)..."

for i in $(seq 1 100000); do
    # Format: userId:username:discriminator:avatar
    MEMBER="user_${i}:TestUser${i}:$(printf "%04d" $((i % 10000))):avatar_${i}"
    echo "SADD guild:test-guild:members \"$MEMBER\""
done | redis-cli --pipe > /dev/null

MEMBER_COUNT=$(redis-cli scard guild:test-guild:members)
echo "✅ Seeded $MEMBER_COUNT members into guild:test-guild:members"

# Add some specific members for query testing
redis-cli sadd guild:test-guild:members "user_admin:AdminUser:0001:admin_avatar" > /dev/null
redis-cli sadd guild:test-guild:members "user_mod:ModeratorUser:0002:mod_avatar" > /dev/null

echo "✅ Redis seeding complete!"
