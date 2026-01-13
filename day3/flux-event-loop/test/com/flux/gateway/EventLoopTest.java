package com.flux.gateway;

import com.flux.gateway.core.Connection;
import com.flux.gateway.core.ConnectionState;
import com.flux.gateway.protocol.ProtocolHandler;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * Unit tests for core event loop components.
 * Run with: java test/com/flux/gateway/EventLoopTest.java
 */
public class EventLoopTest {
    
    public static void main(String[] args) {
        System.out.println("Running Event Loop Tests...\n");
        
        int passed = 0;
        int failed = 0;
        
        // Test 1: Connection state transitions
        try {
            testConnectionStateTransitions();
            System.out.println("✓ Test 1: Connection state transitions");
            passed++;
        } catch (AssertionError e) {
            System.err.println("✗ Test 1 failed: " + e.getMessage());
            failed++;
        }
        
        // Test 2: Protocol frame parsing
        try {
            testProtocolFrameParsing();
            System.out.println("✓ Test 2: Protocol frame parsing");
            passed++;
        } catch (AssertionError e) {
            System.err.println("✗ Test 2 failed: " + e.getMessage());
            failed++;
        }
        
        // Test 3: Partial frame handling
        try {
            testPartialFrameHandling();
            System.out.println("✓ Test 3: Partial frame handling");
            passed++;
        } catch (AssertionError e) {
            System.err.println("✗ Test 3 failed: " + e.getMessage());
            failed++;
        }
        
        // Test 4: Buffer compaction
        try {
            testBufferCompaction();
            System.out.println("✓ Test 4: Buffer compaction");
            passed++;
        } catch (AssertionError e) {
            System.err.println("✗ Test 4 failed: " + e.getMessage());
            failed++;
        }
        
        System.out.println("\n═══════════════════════════════════");
        System.out.println("Tests passed: " + passed);
        System.out.println("Tests failed: " + failed);
        System.out.println("═══════════════════════════════════");
        
        System.exit(failed > 0 ? 1 : 0);
    }
    
    private static void testConnectionStateTransitions() {
        var conn = Connection.create(1, null);
        
        assertEqual(ConnectionState.HANDSHAKE, conn.state());
        
        conn.transitionTo(ConnectionState.READY);
        assertEqual(ConnectionState.READY, conn.state());
        
        conn.transitionTo(ConnectionState.CLOSING);
        assertEqual(ConnectionState.CLOSING, conn.state());
    }
    
    private static void testProtocolFrameParsing() {
        var conn = Connection.create(1, null);
        conn.transitionTo(ConnectionState.HANDSHAKE);
        
        // Simulate receiving handshake message
        String message = "FLUX_HELLO";
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = conn.readBuffer();
        
        buffer.putInt(payload.length);
        buffer.put(payload);
        
        ProtocolHandler handler = new ProtocolHandler();
        int processed = handler.processIncomingData(conn);
        
        assertEqual(1, processed);
        assertEqual(ConnectionState.READY, conn.state());
        assertFalse(conn.writeQueue().isEmpty(), "Write queue should contain response");
    }
    
    private static void testPartialFrameHandling() {
        var conn = Connection.create(1, null);
        var buffer = conn.readBuffer();
        
        // Write partial frame (only length header)
        buffer.putInt(100);
        
        ProtocolHandler handler = new ProtocolHandler();
        int processed = handler.processIncomingData(conn);
        
        assertEqual(0, processed, "Should not process partial frame");
        assertEqual(4, buffer.position(), "Buffer position should preserve partial data");
    }
    
    private static void testBufferCompaction() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        
        // Write some data
        buffer.put("ABCDEFGH".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        
        // Read partial data
        buffer.get(); // Read 'A'
        buffer.get(); // Read 'B'
        
        // Compact should move "CDEFGH" to start
        buffer.compact();
        
        assertEqual(6, buffer.position(), "Position should be at end of remaining data");
        buffer.flip();
        
        byte[] remaining = new byte[buffer.remaining()];
        buffer.get(remaining);
        assertEqual("CDEFGH", new String(remaining, StandardCharsets.UTF_8));
    }
    
    private static void assertEqual(Object expected, Object actual) {
        assertEqual(expected, actual, "Expected " + expected + " but got " + actual);
    }
    
    private static void assertEqual(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message);
        }
    }
    
    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}
