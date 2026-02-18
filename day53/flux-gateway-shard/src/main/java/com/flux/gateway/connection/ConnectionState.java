package com.flux.gateway.connection;

/**
 * Lifecycle states of a Gateway WebSocket connection.
 *
 * State transitions (legal paths only):
 *
 *  HANDSHAKING
 *      │  HTTP upgrade successful
 *      ▼
 *  WAITING_IDENTIFY
 *      │  Opcode 2 received and parsed
 *      ▼
 *  IDENTIFYING          ◄──── only transition allowed from WAITING_IDENTIFY
 *      │  ShardRegistry.claim() returns Claimed or Evicted
 *      ▼
 *  IDENTIFIED
 *      │  READY dispatch sent to client
 *      ▼
 *  READY                ◄──── steady state: heartbeats, events
 *      │  channel closed / error / server-side eviction
 *      ▼
 *  ZOMBIE               ◄──── detected by ShardRegistry probe
 *      │  cleanup complete
 *      ▼
 *  DISCONNECTED         ◄──── terminal state
 */
public enum ConnectionState {
    HANDSHAKING,
    WAITING_IDENTIFY,
    IDENTIFYING,
    IDENTIFIED,
    READY,
    ZOMBIE,
    DISCONNECTED;

    public boolean isTerminal() {
        return this == ZOMBIE || this == DISCONNECTED;
    }
}
