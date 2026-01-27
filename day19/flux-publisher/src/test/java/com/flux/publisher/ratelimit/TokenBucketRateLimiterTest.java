package com.flux.publisher.ratelimit;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {
    
    @Test
    void testBasicTokenAcquisition() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, 100);
        
        // Should acquire 10 tokens successfully
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire());
        }
        
        // Should fail when bucket empty
        assertFalse(limiter.tryAcquire());
        
        limiter.shutdown();
    }
    
    @Test
    void testTokenRelease() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 100);
        
        // Drain bucket
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire();
        }
        assertFalse(limiter.tryAcquire());
        
        // Release token
        limiter.releaseToken();
        assertTrue(limiter.tryAcquire());
        
        limiter.shutdown();
    }
    
    @Test
    void testRefill() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(100, 1000);
        
        // Drain some tokens
        for (int i = 0; i < 50; i++) {
            limiter.tryAcquire();
        }
        
        long before = limiter.availableTokens();
        
        // Wait for refill (100ms interval)
        Thread.sleep(150);
        
        long after = limiter.availableTokens();
        assertTrue(after > before, "Tokens should refill over time");
        
        limiter.shutdown();
    }
}
