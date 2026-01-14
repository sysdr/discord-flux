package com.flux.gateway.handler;

import com.flux.gateway.protocol.GatewayPacket;
import com.flux.gateway.server.ConnectionState;

/**
 * Handles decoded packets. Extensible for future opcodes.
 */
public class PacketHandler {
    
    public void handle(ConnectionState state, GatewayPacket packet) {
        // Future: Add sophisticated routing logic
        System.out.println("Handling packet: " + packet.getClass().getSimpleName());
    }
}
