package com.flux.publisher.ratelimit;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lock-free token bucket rate limiter using VarHandle for atomic operations.
 * Avoids contention and allocation overhead of synchronized or AtomicLong.
 * 
 * Theory: At 100K req/sec, using synchronized would create lock contention.
 * VarHandle provides volatile semantics + CAS without allocation overhead.
 */
public class TokenBucketRateLimiter {
    private static final VarHandle TOKENS_HANDLE;
    
    static {
        try {
            TOKENS_HANDLE = MethodHandles.lookup().findVarHandle(
                TokenBucketRateLimiter.class, "tokens", long.class
            );
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile long tokens;
    private final long maxTokens;
    private final long refillRate; // tokens per second
    private final ScheduledExecutorService refillExecutor;

    public TokenBucketRateLimiter(long maxTokens, long refillRate) {
        this.maxTokens = maxTokens;
        this.refillRate = refillRate;
        this.tokens = maxTokens;
        
        // Refill tokens periodically
        this.refillExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TokenBucket-Refill");
            t.setDaemon(true);
            return t;
        });
        
        // Refill every 100ms
        long refillAmount = refillRate / 10;
        refillExecutor.scheduleAtFixedRate(
            () -> refill(refillAmount),
            100, 100, TimeUnit.MILLISECONDS
        );
    }

    /**
     * Try to acquire a token using lock-free CAS loop.
     * Returns true if token acquired, false if bucket empty.
     */
    public boolean tryAcquire() {
        while (true) {
            long current = (long) TOKENS_HANDLE.getVolatile(this);
            if (current <= 0) {
                return false; // No tokens available
            }
            
            // CAS: if tokens hasn't changed, decrement it
            if (TOKENS_HANDLE.compareAndSet(this, current, current - 1)) {
                return true;
            }
            // CAS failed, retry (another thread modified tokens)
        }
    }

    /**
     * Release a token back to the bucket (on Redis failure).
     */
    public void releaseToken() {
        while (true) {
            long current = (long) TOKENS_HANDLE.getVolatile(this);
            if (current >= maxTokens) {
                return; // Bucket full
            }
            if (TOKENS_HANDLE.compareAndSet(this, current, current + 1)) {
                return;
            }
        }
    }

    /**
     * Refill tokens up to max capacity.
     */
    private void refill(long amount) {
        while (true) {
            long current = (long) TOKENS_HANDLE.getVolatile(this);
            long newValue = Math.min(current + amount, maxTokens);
            if (TOKENS_HANDLE.compareAndSet(this, current, newValue)) {
                return;
            }
        }
    }

    public long availableTokens() {
        return (long) TOKENS_HANDLE.getVolatile(this);
    }

    public void shutdown() {
        refillExecutor.shutdown();
    }
}
