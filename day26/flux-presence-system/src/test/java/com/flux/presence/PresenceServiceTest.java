package com.flux.presence;

import com.flux.presence.core.PresenceService;
import com.flux.presence.core.PresenceStatus;
import org.junit.jupiter.api.*;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PresenceServiceTest {
    
    private static PresenceService service;
    
    @BeforeAll
    static void setup() {
        service = new PresenceService("localhost", 6379);
    }
    
    @AfterAll
    static void teardown() {
        service.close();
    }
    
    @Test
    @Order(1)
    void testMarkOnline() throws ExecutionException, InterruptedException {
        long userId = 999L;
        service.markOnline(userId);
        
        // Wait for batch flush
        Thread.sleep(600);
        
        PresenceStatus status = service.getPresence(userId).get();
        assertEquals(PresenceStatus.ONLINE, status);
    }
    
    @Test
    @Order(2)
    void testL1CacheHit() throws ExecutionException, InterruptedException {
        long userId = 1000L;
        service.markOnline(userId);
        
        // First query - should populate cache
        service.getPresence(userId).get();
        
        // Second query - should hit L1 cache
        var metrics1 = service.getMetrics();
        service.getPresence(userId).get();
        var metrics2 = service.getMetrics();
        
        // Cache hits should increase
        assertTrue(metrics2.cacheHits() > metrics1.cacheHits());
    }
    
    @Test
    @Order(3)
    void testBatchWritePerformance() throws InterruptedException {
        int userCount = 1000;
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < userCount; i++) {
            service.markOnline(2000L + i);
        }
        
        long duration = System.currentTimeMillis() - start;
        System.out.println("Queued " + userCount + " presence updates in " + duration + "ms");
        
        // Should queue instantly (non-blocking)
        assertTrue(duration < 100, "Queueing should be fast");
        
        // Wait for batch flush
        Thread.sleep(1000);
        
        var metrics = service.getMetrics();
        System.out.println("Redis writes after batch: " + metrics.redisWrites());
    }
    
    @Test
    @Order(4)
    void testMarkOffline() throws ExecutionException, InterruptedException {
        long userId = 3000L;
        service.markOnline(userId);
        Thread.sleep(600);
        
        service.markOffline(userId).get();
        
        PresenceStatus status = service.getPresence(userId).get();
        assertEquals(PresenceStatus.OFFLINE, status);
    }
}
