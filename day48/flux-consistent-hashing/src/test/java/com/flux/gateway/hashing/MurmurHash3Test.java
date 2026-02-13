package com.flux.gateway.hashing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MurmurHash3Test {
    
    @Test
    void testHashConsistency() {
        String key = "test-key-123";
        long hash1 = MurmurHash3.hash64(key);
        long hash2 = MurmurHash3.hash64(key);
        
        assertEquals(hash1, hash2);
    }
    
    @Test
    void testDifferentKeysProduceDifferentHashes() {
        long hash1 = MurmurHash3.hash64("key1");
        long hash2 = MurmurHash3.hash64("key2");
        
        assertNotEquals(hash1, hash2);
    }
    
    @Test
    void testAvalancheEffect() {
        // Single bit change should affect ~50% of output bits
        long hash1 = MurmurHash3.hash64("test");
        long hash2 = MurmurHash3.hash64("tesU"); // 't' -> 'U' (1 bit change)
        
        long xor = hash1 ^ hash2;
        int changedBits = Long.bitCount(xor);
        
        // Expect 20-44 bits changed (31.25% - 68.75% range for good avalanche)
        assertTrue(changedBits >= 20 && changedBits <= 44,
            "Changed bits: " + changedBits + ", expected 20-44");
    }
    
    @Test
    void testEmptyString() {
        // Empty string produces a deterministic hash (may be 0 per MurmurHash3 finalization)
        long hash1 = MurmurHash3.hash64("");
        long hash2 = MurmurHash3.hash64("");
        assertEquals(hash1, hash2, "Empty string hash must be deterministic");
    }
}
