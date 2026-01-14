package com.flux.gateway.server;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.channels.SocketChannel;

/**
 * Represents the state of a single Gateway connection.
 * Uses VarHandle for atomic state transitions (lock-free).
 */
public class ConnectionState {
    
    private static final VarHandle STATE;
    
    static {
        try {
            STATE = MethodHandles.lookup()
                .findVarHandle(ConnectionState.class, "state", State.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    public enum State {
        UNAUTHENTICATED,  // Just connected, waiting for Identify
        IDENTIFIED,       // Authenticated, ready for events
        ACTIVE,          // Sending/receiving packets normally
        ZOMBIE           // Connection lost or timeout
    }
    
    private final SocketChannel channel;
    private volatile State state;
    private volatile long lastHeartbeat;
    private volatile long sequenceNumber;
    
    public ConnectionState(SocketChannel channel) {
        this.channel = channel;
        this.state = State.UNAUTHENTICATED;
        this.lastHeartbeat = System.currentTimeMillis();
        this.sequenceNumber = 0;
    }
    
    public boolean transitionTo(State expected, State target) {
        return STATE.compareAndSet(this, expected, target);
    }
    
    public State getState() {
        return state;
    }
    
    public void setState(State state) {
        this.state = state;
    }
    
    public SocketChannel getChannel() {
        return channel;
    }
    
    public void updateHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
        this.sequenceNumber++;
    }
    
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    public long getSequenceNumber() {
        return sequenceNumber;
    }
    
    public boolean isTimedOut(int timeoutMs) {
        return System.currentTimeMillis() - lastHeartbeat > timeoutMs;
    }
}
