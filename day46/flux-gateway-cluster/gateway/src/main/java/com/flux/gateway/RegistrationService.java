package com.flux.gateway;

import com.flux.gateway.models.GatewayRegistration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RegistrationService {
    private static final String REGISTRY_KEY = "gateway:nodes";
    private static final int HEARTBEAT_INTERVAL_SECONDS = 5;
    private static final int TTL_SECONDS = 15;
    
    private final JedisPool jedisPool;
    private final String nodeId;
    private final String ipAddress;
    private final int port;
    private final ConnectionManager connectionManager;
    private final ScheduledExecutorService heartbeatExecutor;
    private final AtomicBoolean isRegistered = new AtomicBoolean(false);
    
    public RegistrationService(String redisHost, int redisPort, String nodeId, 
                              String ipAddress, int port, ConnectionManager connectionManager) {
        var config = new JedisPoolConfig();
        config.setMaxTotal(8);
        config.setMaxIdle(4);
        config.setMinIdle(2);
        
        this.jedisPool = new JedisPool(config, redisHost, redisPort);
        this.nodeId = nodeId;
        this.ipAddress = ipAddress;
        this.port = port;
        this.connectionManager = connectionManager;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r);
            thread.setName("heartbeat-thread");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    public void register() {
        System.out.println("[Registration] Registering node: " + nodeId);
        updateHeartbeat("HEALTHY");
        isRegistered.set(true);
        
        // Schedule periodic heartbeats
        heartbeatExecutor.scheduleAtFixedRate(
            () -> updateHeartbeat("HEALTHY"),
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        System.out.println("[Registration] Node registered successfully with TTL: " + TTL_SECONDS + "s");
    }
    
    public void markDraining() {
        System.out.println("[Registration] Marking node as DRAINING");
        updateHeartbeat("DRAINING");
    }
    
    public void deregister() {
        System.out.println("[Registration] Deregistering node: " + nodeId);
        heartbeatExecutor.shutdown();
        
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel(REGISTRY_KEY, nodeId);
        }
        
        isRegistered.set(false);
        jedisPool.close();
        System.out.println("[Registration] Node deregistered successfully");
    }
    
    private void updateHeartbeat(String status) {
        var registration = new GatewayRegistration(
            nodeId,
            ipAddress,
            port,
            connectionManager.getActiveConnectionCount(),
            Instant.now(),
            status
        );
        
        try (var jedis = jedisPool.getResource()) {
            jedis.hset(REGISTRY_KEY, nodeId, registration.toJson());
            jedis.expire(REGISTRY_KEY, TTL_SECONDS * 2); // Keep the hash itself alive
        } catch (Exception e) {
            System.err.println("[Registration] Failed to update heartbeat: " + e.getMessage());
        }
    }
    
    public boolean isRegistered() {
        return isRegistered.get();
    }
}
