package com.flux.gateway;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HeartbeatManager {
    private static final long HEARTBEAT_INTERVAL_NANOS = 30_000_000_000L; // 30s
    private static final long TIMEOUT_NANOS = 45_000_000_000L; // 45s
    private static final long SCAN_INTERVAL_NANOS = 10_000_000_000L; // 10s
    
    // Pre-allocated heartbeat frame (Opcode 1)
    private static final byte[] HEARTBEAT_FRAME = createHeartbeatFrame();
    private static final ByteBuffer HEARTBEAT_TEMPLATE = 
        ByteBuffer.allocateDirect(HEARTBEAT_FRAME.length)
            .put(HEARTBEAT_FRAME)
            .flip()
            .asReadOnlyBuffer();
    
    private final ConnectionRegistry registry;
    private final GatewayServer server;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Metrics metrics;
    
    public HeartbeatManager(ConnectionRegistry registry, GatewayServer server, Metrics metrics) {
        this.registry = registry;
        this.server = server;
        this.metrics = metrics;
    }
    
    private static byte[] createHeartbeatFrame() {
        // WebSocket frame: FIN=1, Opcode=1 (text), Mask=0, Payload={"op":1}
        String payload = "{\"op\":1}";
        byte[] payloadBytes = payload.getBytes();
        byte[] frame = new byte[2 + payloadBytes.length];
        frame[0] = (byte) 0x81; // FIN + Text frame
        frame[1] = (byte) payloadBytes.length; // Payload length
        System.arraycopy(payloadBytes, 0, frame, 2, payloadBytes.length);
        return frame;
    }
    
    public void start() {
        if (running.compareAndSet(false, true)) {
            startSenderThread();
            startReaperThread();
        }
    }
    
    public void stop() {
        running.set(false);
    }
    
    private void startSenderThread() {
        Thread.ofVirtual().name("heartbeat-sender").start(() -> {
            while (running.get()) {
                long now = System.nanoTime();
                for (int i = 0; i < registry.getMaxConnections(); i++) {
                    Connection conn = registry.getConnection(i);
                    if (conn != null) {
                        long lastSent = registry.getLastHeartbeatSent(i);
                        if (now - lastSent >= HEARTBEAT_INTERVAL_NANOS) {
                            sendHeartbeat(conn);
                        }
                    }
                }
                LockSupport.parkNanos(HEARTBEAT_INTERVAL_NANOS);
            }
        });
    }
    
    private void startReaperThread() {
        Thread.ofVirtual().name("heartbeat-reaper").start(() -> {
            while (running.get()) {
                long now = System.nanoTime();
                int timeouts = 0;
                
                for (int i = 0; i < registry.getMaxConnections(); i++) {
                    Connection conn = registry.getConnection(i);
                    if (conn != null && isTimedOut(i, now)) {
                        server.closeConnection(conn, "heartbeat_timeout");
                        timeouts++;
                    }
                }
                
                if (timeouts > 0) {
                    metrics.recordTimeouts(timeouts);
                }
                
                LockSupport.parkNanos(SCAN_INTERVAL_NANOS);
            }
        });
    }
    
    private void sendHeartbeat(Connection conn) {
        try {
            ByteBuffer frame = HEARTBEAT_TEMPLATE.duplicate();
            conn.channel().write(frame);
            registry.recordHeartbeatSent(conn.id());
            metrics.incrementHeartbeatsSent();
        } catch (Exception e) {
            System.err.println("Failed to send heartbeat to " + conn.id() + ": " + e.getMessage());
        }
    }
    
    private boolean isTimedOut(int connectionId, long now) {
        long lastAck = registry.getLastAckReceived(connectionId);
        return (now - lastAck) > TIMEOUT_NANOS;
    }
    
    public void handleAck(int connectionId) {
        registry.recordAck(connectionId);
        metrics.incrementAcksReceived();
    }
}
