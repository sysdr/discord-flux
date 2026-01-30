package com.flux.gateway;

import com.flux.gateway.buffer.ConnectionState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.channels.SocketChannel;

class ConnectionStateTest {

    @Test
    void testRingBufferWrite() throws Exception {
        SocketChannel mockChannel = SocketChannel.open();
        ConnectionState state = new ConnectionState("test-1", mockChannel);

        byte[] data = "Hello World".getBytes();
        boolean written = state.tryWrite(data);

        assertTrue(written);
        assertEquals(data.length, state.getBufferUsage());
    }

    @Test
    void testLagCounterIncrement() throws Exception {
        SocketChannel mockChannel = SocketChannel.open();
        ConnectionState state = new ConnectionState("test-2", mockChannel);

        byte[] largeData = new byte[60000];
        state.tryWrite(largeData);

        assertTrue(state.getLagCounter() > 0);
    }

    @Test
    void testBufferOverflow() throws Exception {
        SocketChannel mockChannel = SocketChannel.open();
        ConnectionState state = new ConnectionState("test-3", mockChannel);

        byte[] data = new byte[70000];
        boolean written = state.tryWrite(data);

        assertFalse(written);
        assertTrue(state.getLagCounter() > 0);
    }
}
