package com.flux.netpoll;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ConnectionRegistry {
    private final ConcurrentHashMap<Long, ChannelHandler> connections = new ConcurrentHashMap<>();
    private final AtomicLong connectionIdGenerator = new AtomicLong(0);

    public long nextConnectionId() {
        return connectionIdGenerator.incrementAndGet();
    }

    public void register(ChannelHandler handler) {
        connections.put(handler.connectionId(), handler);
    }

    public void unregister(long connectionId) {
        connections.remove(connectionId);
    }

    public int activeCount() {
        return connections.size();
    }

    public ChannelHandler[] getActiveConnections() {
        return connections.values().toArray(new ChannelHandler[0]);
    }
}
