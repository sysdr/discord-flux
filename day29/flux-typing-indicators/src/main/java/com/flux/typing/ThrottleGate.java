package com.flux.typing;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentHashMap;

public class ThrottleGate {
    private static final long THROTTLE_NANOS = 3_000_000_000L; // 3 seconds
    
    private final ConcurrentHashMap<Long, ThrottleState> userStates;
    
    private static class ThrottleState {
        volatile long lastEventNanos;
        
        private static final VarHandle LAST_EVENT;
        
        static {
            try {
                LAST_EVENT = MethodHandles.lookup()
                    .findVarHandle(ThrottleState.class, "lastEventNanos", long.class);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        
        boolean tryAcquire(long now) {
            long last = (long) LAST_EVENT.getOpaque(this);
            if (now - last < THROTTLE_NANOS) return false;
            return LAST_EVENT.compareAndSet(this, last, now);
        }
    }
    
    public ThrottleGate() {
        this.userStates = new ConcurrentHashMap<>();
    }
    
    public boolean tryAcquire(long userId) {
        long now = System.nanoTime();
        ThrottleState state = userStates.computeIfAbsent(userId, k -> new ThrottleState());
        return state.tryAcquire(now);
    }
    
    public void cleanup(long cutoffNanos) {
        userStates.entrySet().removeIf(entry -> 
            System.nanoTime() - entry.getValue().lastEventNanos > cutoffNanos
        );
    }
}
