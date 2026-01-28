package com.flux.gateway.connection;

import com.flux.gateway.model.GatewayEvent;
import com.flux.gateway.intent.GatewayIntent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.LongAdder;

public class GatewayConnection {
    private static final VarHandle INTENTS;
    
    static {
        try {
            var lookup = MethodHandles.lookup();
            INTENTS = lookup.findVarHandle(GatewayConnection.class, "intents", long.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final String userId;
    private final boolean isVerified;
    private volatile long intents;
    
    // Metrics
    private final LongAdder eventsSent = new LongAdder();
    private final LongAdder eventsFiltered = new LongAdder();
    private final LongAdder bandwidthSent = new LongAdder();
    private final LongAdder bandwidthSaved = new LongAdder();
    
    // Simulated send buffer
    private final StringBuilder sendBuffer = new StringBuilder();

    public GatewayConnection(String userId, long intents, boolean isVerified) {
        this.userId = userId;
        this.isVerified = isVerified;
        updateIntents(intents);
    }

    public void updateIntents(long newIntents) {
        // Validate privileged intents
        if (!isVerified && GatewayIntent.isPrivileged(newIntents)) {
            throw new IllegalArgumentException(
                "Unverified connection cannot request privileged intents: " +
                GatewayIntent.describe(newIntents & GatewayIntent.getPrivilegedMask())
            );
        }
        INTENTS.setRelease(this, newIntents);
    }

    public long getIntents() {
        return (long) INTENTS.getAcquire(this);
    }

    public boolean shouldReceive(GatewayEvent event) {
        long currentIntents = getIntents();
        return (currentIntents & event.requiredIntent()) != 0;
    }

    public void send(GatewayEvent event) {
        // Simulate serialization and sending
        sendBuffer.setLength(0);
        sendBuffer.append("{\"t\":\"").append(event.type()).append("\",");
        sendBuffer.append("\"d\":").append(event.data().toString()).append("}");
        
        eventsSent.increment();
        bandwidthSent.add(event.estimatedSize());
    }

    public void filterEvent(GatewayEvent event) {
        eventsFiltered.increment();
        bandwidthSaved.add(event.estimatedSize());
    }

    public String getUserId() {
        return userId;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public long getEventsSent() {
        return eventsSent.sum();
    }

    public long getEventsFiltered() {
        return eventsFiltered.sum();
    }

    public long getBandwidthSent() {
        return bandwidthSent.sum();
    }

    public long getBandwidthSaved() {
        return bandwidthSaved.sum();
    }

    public void resetMetrics() {
        eventsSent.reset();
        eventsFiltered.reset();
        bandwidthSent.reset();
        bandwidthSaved.reset();
    }
}
