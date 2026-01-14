package com.flux.gateway;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConnectionRegistry {
    private static final int MAX_CONNECTIONS = 100_000;
    private static final VarHandle HEARTBEAT_SENT_VH;
    private static final VarHandle ACK_RECEIVED_VH;
    
    static {
        try {
            HEARTBEAT_SENT_VH = MethodHandles.arrayElementVarHandle(long[].class);
            ACK_RECEIVED_VH = MethodHandles.arrayElementVarHandle(long[].class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private final long[] lastHeartbeatSent;
    private final long[] lastAckReceived;
    private final Connection[] connections;
    private final AtomicInteger activeCount = new AtomicInteger(0);
    
    public ConnectionRegistry() {
        this.lastHeartbeatSent = new long[MAX_CONNECTIONS];
        this.lastAckReceived = new long[MAX_CONNECTIONS];
        this.connections = new Connection[MAX_CONNECTIONS];
    }
    
    public synchronized int register(Connection conn) {
        connections[conn.id()] = conn;
        long now = System.nanoTime();
        HEARTBEAT_SENT_VH.setRelease(lastHeartbeatSent, conn.id(), now);
        ACK_RECEIVED_VH.setRelease(lastAckReceived, conn.id(), now);
        activeCount.incrementAndGet();
        return conn.id();
    }
    
    public void recordHeartbeatSent(int connectionId) {
        HEARTBEAT_SENT_VH.setRelease(lastHeartbeatSent, connectionId, System.nanoTime());
    }
    
    public void recordAck(int connectionId) {
        ACK_RECEIVED_VH.setRelease(lastAckReceived, connectionId, System.nanoTime());
    }
    
    public long getLastHeartbeatSent(int id) {
        return (long) HEARTBEAT_SENT_VH.getAcquire(lastHeartbeatSent, id);
    }
    
    public long getLastAckReceived(int id) {
        return (long) ACK_RECEIVED_VH.getAcquire(lastAckReceived, id);
    }
    
    public Connection getConnection(int id) {
        return connections[id];
    }
    
    public synchronized void unregister(int id) {
        connections[id] = null;
        activeCount.decrementAndGet();
    }
    
    public int getActiveCount() {
        return activeCount.get();
    }
    
    public int getMaxConnections() {
        return MAX_CONNECTIONS;
    }
}
