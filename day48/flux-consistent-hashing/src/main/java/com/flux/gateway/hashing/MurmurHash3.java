package com.flux.gateway.hashing;

import java.nio.charset.StandardCharsets;

/**
 * MurmurHash3 128-bit hash implementation.
 * We use only the first 64 bits for the hash ring.
 * 
 * Why MurmurHash3?
 * - Excellent avalanche properties (1-bit input change affects 50% of output bits)
 * - Fast: ~3 CPU cycles per byte on modern x86
 * - Better distribution than Java's default hashCode() which has known weaknesses
 */
public final class MurmurHash3 {
    
    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;
    
    private MurmurHash3() {}
    
    public static long hash64(String key) {
        return hash64(key.getBytes(StandardCharsets.UTF_8));
    }
    
    public static long hash64(byte[] data) {
        return hash64(data, 0, data.length, 0);
    }
    
    /**
     * 64-bit hash using the first 64 bits of MurmurHash3's 128-bit output.
     */
    public static long hash64(byte[] data, int offset, int length, int seed) {
        long h1 = seed & 0xFFFFFFFFL;
        long h2 = seed & 0xFFFFFFFFL;
        
        final int nblocks = length / 16;
        
        // Body: process 16-byte blocks
        for (int i = 0; i < nblocks; i++) {
            int index = offset + i * 16;
            
            long k1 = getLong(data, index);
            long k2 = getLong(data, index + 8);
            
            // Mix k1
            k1 *= C1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= C2;
            h1 ^= k1;
            
            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;
            
            // Mix k2
            k2 *= C2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= C1;
            h2 ^= k2;
            
            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
        }
        
        // Tail: process remaining bytes
        long k1 = 0;
        long k2 = 0;
        
        int index = offset + nblocks * 16;
        int remaining = length - nblocks * 16;
        
        switch (remaining) {
            case 15: k2 ^= ((long) data[index + 14] & 0xff) << 48;
            case 14: k2 ^= ((long) data[index + 13] & 0xff) << 40;
            case 13: k2 ^= ((long) data[index + 12] & 0xff) << 32;
            case 12: k2 ^= ((long) data[index + 11] & 0xff) << 24;
            case 11: k2 ^= ((long) data[index + 10] & 0xff) << 16;
            case 10: k2 ^= ((long) data[index + 9] & 0xff) << 8;
            case 9:  k2 ^= ((long) data[index + 8] & 0xff);
                k2 *= C2;
                k2 = Long.rotateLeft(k2, 33);
                k2 *= C1;
                h2 ^= k2;
            case 8:  k1 ^= ((long) data[index + 7] & 0xff) << 56;
            case 7:  k1 ^= ((long) data[index + 6] & 0xff) << 48;
            case 6:  k1 ^= ((long) data[index + 5] & 0xff) << 40;
            case 5:  k1 ^= ((long) data[index + 4] & 0xff) << 32;
            case 4:  k1 ^= ((long) data[index + 3] & 0xff) << 24;
            case 3:  k1 ^= ((long) data[index + 2] & 0xff) << 16;
            case 2:  k1 ^= ((long) data[index + 1] & 0xff) << 8;
            case 1:  k1 ^= ((long) data[index] & 0xff);
                k1 *= C1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= C2;
                h1 ^= k1;
        }
        
        // Finalization
        h1 ^= length;
        h2 ^= length;
        
        h1 += h2;
        h2 += h1;
        
        h1 = fmix64(h1);
        h2 = fmix64(h2);
        
        h1 += h2;
        
        return h1;
    }
    
    private static long getLong(byte[] data, int index) {
        return ((long) data[index] & 0xff)
            | (((long) data[index + 1] & 0xff) << 8)
            | (((long) data[index + 2] & 0xff) << 16)
            | (((long) data[index + 3] & 0xff) << 24)
            | (((long) data[index + 4] & 0xff) << 32)
            | (((long) data[index + 5] & 0xff) << 40)
            | (((long) data[index + 6] & 0xff) << 48)
            | (((long) data[index + 7] & 0xff) << 56);
    }
    
    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }
}
