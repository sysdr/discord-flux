package com.flux.gateway.buffer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.channels.SocketChannel;

public class ConnectionState {
    private static final int RING_BUFFER_SIZE = 65536; // 64 KB, must be power of 2
    private static final int CAPACITY_MASK = RING_BUFFER_SIZE - 1;
    private static final double LAG_THRESHOLD_PERCENT = 0.8;

    private static final VarHandle LAG_COUNTER_HANDLE;
    private static final VarHandle HEAD_HANDLE;
    private static final VarHandle TAIL_HANDLE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            LAG_COUNTER_HANDLE = lookup.findVarHandle(ConnectionState.class, "lagCounter", long.class);
            HEAD_HANDLE = lookup.findVarHandle(ConnectionState.class, "head", long.class);
            TAIL_HANDLE = lookup.findVarHandle(ConnectionState.class, "tail", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final String connectionId;
    private final SocketChannel channel;
    private final byte[] ringBuffer;
    private volatile long head;  // Read pointer
    private volatile long tail;  // Write pointer
    private volatile long lagCounter;
    private final long createdAt;
    private volatile boolean closed;

    public ConnectionState(String connectionId, SocketChannel channel) {
        this.connectionId = connectionId;
        this.channel = channel;
        this.ringBuffer = new byte[RING_BUFFER_SIZE];
        this.head = 0;
        this.tail = 0;
        this.lagCounter = 0;
        this.createdAt = System.currentTimeMillis();
        this.closed = false;
    }

    public boolean tryWrite(byte[] data) {
        if (closed) return false;

        long currentTail = (long) TAIL_HANDLE.getVolatile(this);
        long currentHead = (long) HEAD_HANDLE.getVolatile(this);

        long available = RING_BUFFER_SIZE - (currentTail - currentHead);

        if (available < data.length) {
            LAG_COUNTER_HANDLE.getAndAdd(this, 1L);
            return false;
        }

        for (byte b : data) {
            int index = (int) (currentTail & CAPACITY_MASK);
            ringBuffer[index] = b;
            currentTail++;
        }

        TAIL_HANDLE.setVolatile(this, currentTail);

        long usage = currentTail - currentHead;
        if (usage > RING_BUFFER_SIZE * LAG_THRESHOLD_PERCENT) {
            LAG_COUNTER_HANDLE.getAndAdd(this, 1L);
        }

        return true;
    }

    public byte[] read(int maxBytes) {
        long currentHead = (long) HEAD_HANDLE.getVolatile(this);
        long currentTail = (long) TAIL_HANDLE.getVolatile(this);

        long available = currentTail - currentHead;
        if (available == 0) return new byte[0];

        int toRead = (int) Math.min(available, maxBytes);
        byte[] result = new byte[toRead];

        for (int i = 0; i < toRead; i++) {
            int index = (int) ((currentHead + i) & CAPACITY_MASK);
            result[i] = ringBuffer[index];
        }

        HEAD_HANDLE.setVolatile(this, currentHead + toRead);
        return result;
    }

    public long getLagCounter() {
        return (long) LAG_COUNTER_HANDLE.getVolatile(this);
    }

    public void resetLagCounter() {
        LAG_COUNTER_HANDLE.setVolatile(this, 0L);
    }

    public long getBufferUsage() {
        long currentTail = (long) TAIL_HANDLE.getVolatile(this);
        long currentHead = (long) HEAD_HANDLE.getVolatile(this);
        return currentTail - currentHead;
    }

    public double getBufferUsagePercent() {
        return (double) getBufferUsage() / RING_BUFFER_SIZE * 100.0;
    }

    public String getConnectionId() { return connectionId; }
    public SocketChannel getChannel() { return channel; }
    public long getCreatedAt() { return createdAt; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
}
