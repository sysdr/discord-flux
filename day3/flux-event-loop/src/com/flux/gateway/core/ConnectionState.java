package com.flux.gateway.core;

/**
 * Represents the lifecycle states of a WebSocket-style connection.
 * State transitions:
 * CONNECTING -> HANDSHAKE -> READY -> CLOSING -> CLOSED
 */
public enum ConnectionState {
    CONNECTING,  // Initial state, socket accepted but not validated
    HANDSHAKE,   // Performing protocol handshake
    READY,       // Fully connected and ready to exchange messages
    CLOSING,     // Graceful shutdown initiated
    CLOSED       // Connection terminated
}
