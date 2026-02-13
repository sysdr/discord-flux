package com.flux.discovery;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Core service registry managing Gateway node lifecycle.
 * Uses Redis as the distributed state store with TTL-based leases.
 */
public class ServiceRegistry implements AutoCloseable {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);
    private static final String KEY_PREFIX = "gateway:nodes:";
    private static final String EVENT_CHANNEL = "gateway:events";
    private static final int LEASE_SECONDS = 10;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(3);
    
    private final JedisPool pool;
    private final Gson gson;
    private final AtomicBoolean running;
    private final Map<String, Thread> heartbeatThreads;
    private final AtomicLong registrationCount;
    private final AtomicLong heartbeatSuccessCount;
    private final AtomicLong heartbeatFailureCount;
    
    public ServiceRegistry(String redisHost, int redisPort) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(256); // Allow many concurrent heartbeats + API + pub/sub
        this.pool = new JedisPool(poolConfig, redisHost, redisPort);
        this.gson = new Gson();
        this.running = new AtomicBoolean(true);
        this.heartbeatThreads = new ConcurrentHashMap<>();
        this.registrationCount = new AtomicLong(0);
        this.heartbeatSuccessCount = new AtomicLong(0);
        this.heartbeatFailureCount = new AtomicLong(0);
        
        log.info("ServiceRegistry initialized for {}:{}", redisHost, redisPort);
    }
    
    /**
     * Register a node with atomic operation and spawn heartbeat worker.
     */
    public void register(ServiceNode node) {
        String key = KEY_PREFIX + node.id();
        String value = serializeNode(node);
        
        try (Jedis jedis = pool.getResource()) {
            // Atomic registration with lease
            String result = jedis.set(key, value, 
                SetParams.setParams().nx().ex(LEASE_SECONDS));
            
            if (result == null) {
                // Key exists, force update
                jedis.setex(key, LEASE_SECONDS, value);
                log.warn("Node {} already registered, updating", node.id());
            }
            
            // Publish JOIN event
            jedis.publish(EVENT_CHANNEL, "JOIN:" + node.id());
            
            registrationCount.incrementAndGet();
            log.info("Registered node: {} at {}", node.id(), node.endpoint());
            
            // Start heartbeat worker (Virtual Thread)
            startHeartbeat(node.id());
            
        } catch (JedisException e) {
            log.error("Registration failed for {}: {}", node.id(), e.getMessage());
            throw new RegistrationException("Failed to register node", e);
        }
    }
    
    /**
     * Deregister a node and stop its heartbeat.
     */
    public void deregister(String nodeId) {
        String key = KEY_PREFIX + nodeId;
        
        // Stop heartbeat first
        stopHeartbeat(nodeId);
        
        try (Jedis jedis = pool.getResource()) {
            Long deleted = jedis.del(key);
            if (deleted > 0) {
                jedis.publish(EVENT_CHANNEL, "LEAVE:" + nodeId);
                log.info("Deregistered node: {}", nodeId);
            }
        } catch (JedisException e) {
            log.error("Deregistration failed for {}: {}", nodeId, e.getMessage());
        }
    }
    
    /**
     * Discover all active nodes using SCAN (non-blocking).
     */
    public List<ServiceNode> discover() {
        try (Jedis jedis = pool.getResource()) {
            ScanParams params = new ScanParams()
                .match(KEY_PREFIX + "*")
                .count(100);
            
            List<ServiceNode> nodes = new ArrayList<>();
            String cursor = ScanParams.SCAN_POINTER_START;
            
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                List<String> keys = result.getResult();
                
                // Batch GET using pipeline
                if (!keys.isEmpty()) {
                    Pipeline pipe = jedis.pipelined();
                    List<Response<String>> responses = new ArrayList<>();
                    for (String key : keys) {
                        responses.add(pipe.get(key));
                    }
                    pipe.sync();
                    
                    for (Response<String> response : responses) {
                        String value = response.get();
                        if (value != null) {
                            nodes.add(deserializeNode(value));
                        }
                    }
                }
                
                cursor = result.getCursor();
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
            
            log.debug("Discovered {} active nodes", nodes.size());
            return nodes;
            
        } catch (JedisException e) {
            log.error("Discovery failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get a specific node by ID.
     */
    public Optional<ServiceNode> getNode(String nodeId) {
        String key = KEY_PREFIX + nodeId;
        
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(key);
            return value != null ? Optional.of(deserializeNode(value)) : Optional.empty();
        } catch (JedisException e) {
            log.error("Failed to get node {}: {}", nodeId, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Subscribe to node lifecycle events.
     */
    public void subscribeToEvents(NodeEventListener listener) {
        Thread.startVirtualThread(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        String[] parts = message.split(":", 2);
                        if (parts.length == 2) {
                            String event = parts[0];
                            String nodeId = parts[1];
                            
                            switch (event) {
                                case "JOIN" -> listener.onNodeJoined(nodeId);
                                case "LEAVE" -> listener.onNodeLeft(nodeId);
                            }
                        }
                    }
                }, EVENT_CHANNEL);
            } catch (JedisException e) {
                log.error("Event subscription error: {}", e.getMessage());
            }
        });
    }
    
    /**
     * Get registry metrics.
     */
    public RegistryMetrics getMetrics() {
        return new RegistryMetrics(
            registrationCount.get(),
            heartbeatSuccessCount.get(),
            heartbeatFailureCount.get(),
            heartbeatThreads.size()
        );
    }
    
    private void startHeartbeat(String nodeId) {
        Thread heartbeatThread = Thread.startVirtualThread(() -> {
            String key = KEY_PREFIX + nodeId;
            log.debug("Heartbeat started for {}", nodeId);
            
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try (Jedis jedis = pool.getResource()) {
                    Long ttl = jedis.expire(key, LEASE_SECONDS);
                    
                    if (ttl == 0) {
                        // Key vanished
                        log.warn("Heartbeat: key {} vanished, re-registering", nodeId);
                        heartbeatFailureCount.incrementAndGet();
                        // In production, trigger re-registration here
                        break;
                    } else {
                        heartbeatSuccessCount.incrementAndGet();
                    }
                    
                } catch (JedisException e) {
                    log.error("Heartbeat error for {}: {}", nodeId, e.getMessage());
                    heartbeatFailureCount.incrementAndGet();
                }
                
                // Park instead of sleep (more efficient)
                LockSupport.parkNanos(HEARTBEAT_INTERVAL.toNanos());
            }
            
            log.debug("Heartbeat stopped for {}", nodeId);
        });
        
        heartbeatThreads.put(nodeId, heartbeatThread);
    }
    
    private void stopHeartbeat(String nodeId) {
        Thread thread = heartbeatThreads.remove(nodeId);
        if (thread != null) {
            thread.interrupt();
        }
    }
    
    private String serializeNode(ServiceNode node) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", node.id());
        map.put("host", node.host());
        map.put("port", node.port());
        map.put("registeredAt", node.registeredAt());
        map.put("status", node.status().name());
        return gson.toJson(map);
    }
    
    private ServiceNode deserializeNode(String json) {
        Map<String, Object> map = gson.fromJson(json, Map.class);
        return new ServiceNode(
            (String) map.get("id"),
            (String) map.get("host"),
            ((Double) map.get("port")).intValue(),
            ((Double) map.get("registeredAt")).longValue(),
            NodeStatus.valueOf((String) map.get("status")),
            java.nio.ByteBuffer.allocate(0)
        );
    }
    
    @Override
    public void close() {
        running.set(false);
        heartbeatThreads.values().forEach(Thread::interrupt);
        pool.close();
        log.info("ServiceRegistry closed");
    }
}

record RegistryMetrics(
    long registrations,
    long heartbeatSuccesses,
    long heartbeatFailures,
    int activeHeartbeats
) {}

interface NodeEventListener {
    void onNodeJoined(String nodeId);
    void onNodeLeft(String nodeId);
}

class RegistrationException extends RuntimeException {
    public RegistrationException(String message) {
        super(message);
    }
    
    public RegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
