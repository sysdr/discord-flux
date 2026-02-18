package com.flux.gateway.shard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShardIdentity")
class ShardIdentityTest {

    @Test
    @DisplayName("Valid shard identity is created without exception")
    void validIdentityCreated() {
        var identity = new ShardIdentity(0, 16);
        assertEquals(0, identity.shardId());
        assertEquals(16, identity.numShards());
    }

    @Test
    @DisplayName("shardId >= numShards is rejected")
    void rejectOutOfRangeShardId() {
        assertThrows(IllegalArgumentException.class, () -> new ShardIdentity(16, 16));
        assertThrows(IllegalArgumentException.class, () -> new ShardIdentity(17, 16));
    }

    @Test
    @DisplayName("Negative shardId is rejected")
    void rejectNegativeShardId() {
        assertThrows(IllegalArgumentException.class, () -> new ShardIdentity(-1, 16));
    }

    @Test
    @DisplayName("numShards=0 is rejected")
    void rejectZeroNumShards() {
        assertThrows(IllegalArgumentException.class, () -> new ShardIdentity(0, 0));
    }

    @Test
    @DisplayName("isResponsibleFor correctly routes guild to shard")
    void guildRoutingCorrect() {
        // guildId=41771983423143937L: (guildId >> 22) % 16
        long guildId = 41771983423143937L;
        long expected = (guildId >> 22) % 16;
        var identity = new ShardIdentity((int) expected, 16);
        assertTrue(identity.isResponsibleFor(guildId));

        // Neighboring shard should not be responsible
        int neighborShard = (int) ((expected + 1) % 16);
        var neighbor = new ShardIdentity(neighborShard, 16);
        assertFalse(neighbor.isResponsibleFor(guildId));
    }

    @Test
    @DisplayName("ShardIdentity records with same values are equal")
    void recordEquality() {
        var a = new ShardIdentity(3, 16);
        var b = new ShardIdentity(3, 16);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("toString uses bracket notation matching Discord spec")
    void toStringFormat() {
        assertEquals("[3, 16]", new ShardIdentity(3, 16).toString());
    }
}
