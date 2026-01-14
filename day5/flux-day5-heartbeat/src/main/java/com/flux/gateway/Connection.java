package com.flux.gateway;

import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;

public record Connection(
    int id,
    SocketChannel channel,
    ByteBuffer readBuffer,
    long createdAtNanos
) {
    public Connection(int id, SocketChannel channel) {
        this(id, channel, ByteBuffer.allocateDirect(4096), System.nanoTime());
    }
}
