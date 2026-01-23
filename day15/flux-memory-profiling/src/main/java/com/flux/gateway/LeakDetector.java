package com.flux.gateway;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Uses PhantomReference to detect when sessions should have been GC'd but weren't.
 * This is the "canary in the coal mine" for memory leaks.
 */
public class LeakDetector {
    
    record SessionSentinel(String id) {}
    
    private final ReferenceQueue<SessionSentinel> queue = new ReferenceQueue<>();
    private final ConcurrentHashMap<String, PhantomReference<SessionSentinel>> sentinels = 
        new ConcurrentHashMap<>();
    
    public void track(String sessionId) {
        SessionSentinel sentinel = new SessionSentinel(sessionId);
        PhantomReference<SessionSentinel> ref = new PhantomReference<>(sentinel, queue);
        sentinels.put(sessionId, ref);
    }
    
    public void verify() {
        // Suggest GC (doesn't guarantee it runs)
        System.gc();
        
        try {
            Thread.sleep(100); // Give GC time to run
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // Check which sentinels were collected
        int collected = 0;
        Reference<?> ref;
        while ((ref = queue.poll()) != null) {
            collected++;
            final Reference<?> finalRef = ref;
            sentinels.values().removeIf(r -> r == finalRef);
        }
        
        if (collected > 0) {
            System.out.println("[LeakDetector] Collected " + collected + " sentinels (sessions GC'd correctly)");
        }
        
        // Remaining sentinels = leaked sessions
        int leaked = sentinels.size();
        if (leaked > 100) {
            System.err.println("[LeakDetector] WARNING: " + leaked + 
                " sessions leaked (sentinels not collected after GC)");
        }
    }
    
    public int getLeakedCount() {
        return sentinels.size();
    }
}
