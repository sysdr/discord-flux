package com.flux.discovery;

import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ServiceRegistry.
 * Requires Redis running on localhost:6379.
 */
public class ServiceRegistryTest {
    
    private ServiceRegistry registry;
    private static final String TEST_REDIS_HOST = "localhost";
    private static final int TEST_REDIS_PORT = 6379;
    
    @BeforeAll
    static void checkRedis() {
        try (JedisPool pool = new JedisPool(TEST_REDIS_HOST, TEST_REDIS_PORT)) {
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }
        } catch (Exception e) {
            throw new RuntimeException("Redis not available. Start Redis before running tests.", e);
        }
    }
    
    @BeforeEach
    void setup() {
        try (JedisPool pool = new JedisPool(TEST_REDIS_HOST, TEST_REDIS_PORT)) {
            try (Jedis jedis = pool.getResource()) {
                jedis.flushDB();
            }
        }
        registry = new ServiceRegistry(TEST_REDIS_HOST, TEST_REDIS_PORT);
    }
    
    @AfterEach
    void teardown() {
        if (registry != null) {
            registry.close();
        }
    }
    
    @Test
    @DisplayName("Should register a node successfully")
    void testRegistration() {
        ServiceNode node = new ServiceNode("test-node-1", "localhost", 9000);
        
        assertDoesNotThrow(() -> registry.register(node));
        
        Optional<ServiceNode> retrieved = registry.getNode("test-node-1");
        assertTrue(retrieved.isPresent());
        assertEquals(node.id(), retrieved.get().id());
        assertEquals(node.host(), retrieved.get().host());
        assertEquals(node.port(), retrieved.get().port());
    }
    
    @Test
    @DisplayName("Should discover registered nodes")
    void testDiscovery() throws InterruptedException {
        ServiceNode node1 = new ServiceNode("test-node-1", "localhost", 9001);
        ServiceNode node2 = new ServiceNode("test-node-2", "localhost", 9002);
        ServiceNode node3 = new ServiceNode("test-node-3", "localhost", 9003);
        
        registry.register(node1);
        registry.register(node2);
        registry.register(node3);
        
        Thread.sleep(100); // Allow registration to complete
        
        List<ServiceNode> discovered = registry.discover();
        assertEquals(3, discovered.size());
    }
    
    @Test
    @DisplayName("Should deregister a node")
    void testDeregistration() throws InterruptedException {
        ServiceNode node = new ServiceNode("test-node-1", "localhost", 9000);
        registry.register(node);
        
        Thread.sleep(100);
        
        Optional<ServiceNode> retrieved = registry.getNode("test-node-1");
        assertTrue(retrieved.isPresent());
        
        registry.deregister("test-node-1");
        Thread.sleep(100);
        
        Optional<ServiceNode> afterDeregister = registry.getNode("test-node-1");
        assertFalse(afterDeregister.isPresent());
    }
    
    @Test
    @DisplayName("Should handle concurrent registrations")
    void testConcurrentRegistrations() throws InterruptedException {
        int nodeCount = 50;
        Thread[] threads = new Thread[nodeCount];
        
        for (int i = 0; i < nodeCount; i++) {
            final int idx = i;
            threads[i] = Thread.startVirtualThread(() -> {
                ServiceNode node = new ServiceNode(
                    "concurrent-node-" + idx,
                    "localhost",
                    10000 + idx
                );
                registry.register(node);
            });
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        Thread.sleep(500);
        
        List<ServiceNode> discovered = registry.discover();
        assertEquals(nodeCount, discovered.size());
    }
    
    @Test
    @DisplayName("Should track metrics correctly")
    void testMetrics() throws InterruptedException {
        ServiceNode node1 = new ServiceNode("metrics-node-1", "localhost", 9001);
        ServiceNode node2 = new ServiceNode("metrics-node-2", "localhost", 9002);
        
        registry.register(node1);
        registry.register(node2);
        
        Thread.sleep(500); // Allow some heartbeats
        
        RegistryMetrics metrics = registry.getMetrics();
        assertEquals(2, metrics.registrations());
        assertTrue(metrics.heartbeatSuccesses() > 0);
        assertEquals(2, metrics.activeHeartbeats());
    }
}
