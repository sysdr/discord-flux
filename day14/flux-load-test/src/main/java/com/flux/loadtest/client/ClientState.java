package com.flux.loadtest.client;

import java.time.Instant;

/**
 * State machine for WebSocket client lifecycle.
 * Sealed interface ensures exhaustive pattern matching.
 */
public sealed interface ClientState permits 
    ClientState.Init,
    ClientState.Connecting,
    ClientState.Handshaking,
    ClientState.Open,
    ClientState.Closing,
    ClientState.Closed,
    ClientState.Error {
    
    record Init() implements ClientState {}
    record Connecting(Instant startedAt) implements ClientState {}
    record Handshaking(Instant startedAt) implements ClientState {}
    record Open(Instant connectedAt, long messagesSent, long messagesReceived) implements ClientState {}
    record Closing(Instant startedAt) implements ClientState {}
    record Closed(Instant closedAt, long totalMessages) implements ClientState {}
    record Error(Instant occurredAt, String reason, Throwable cause) implements ClientState {}
}
