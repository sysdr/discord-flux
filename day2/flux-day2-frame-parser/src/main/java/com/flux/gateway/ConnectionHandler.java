package com.flux.gateway;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles a single WebSocket connection using Virtual Thread blocking I/O.
 * Each instance runs in its own virtual thread.
 */
public class ConnectionHandler implements Runnable {
    
    private static final AtomicLong CONNECTION_COUNTER = new AtomicLong(0);
    
    private final long connectionId;
    private final SocketChannel channel;
    private final SocketAddress remoteAddress;
    private final FrameParser parser;
    private final ByteBuffer readBuffer;
    private final MetricsCollector metrics;

    public ConnectionHandler(SocketChannel channel, MetricsCollector metrics) throws IOException {
        this.connectionId = CONNECTION_COUNTER.incrementAndGet();
        this.channel = channel;
        this.remoteAddress = channel.getRemoteAddress();
        this.parser = new FrameParser();
        this.readBuffer = ByteBuffer.allocateDirect(8192); // Off-heap buffer
        this.metrics = metrics;
        
        metrics.incrementConnections();
    }

    @Override
    public void run() {
        try {
            System.out.printf("[Connection %d] Accepted from %s%n", connectionId, remoteAddress);
            
            while (channel.isConnected()) {
                readBuffer.clear();
                int bytesRead = channel.read(readBuffer);
                
                if (bytesRead == -1) {
                    System.out.printf("[Connection %d] Client disconnected%n", connectionId);
                    break;
                }

                if (bytesRead > 0) {
                    metrics.addBytes(bytesRead);
                    readBuffer.flip();
                    processBuffer(readBuffer);
                }
            }
        } catch (IOException e) {
            System.err.printf("[Connection %d] I/O error: %s%n", connectionId, e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void processBuffer(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            WebSocketFrame frame = parser.parse(buffer);
            
            if (frame != null) {
                handleFrame(frame);
            }
        }
    }

    private void handleFrame(WebSocketFrame frame) throws IOException {
        metrics.incrementFrames();
        int payloadSize = frame.payload().remaining();
        
        System.out.printf("[Connection %d] Frame: %s, FIN=%b, Masked=%b, Length=%d%n",
            connectionId, frame.opcodeToString(), frame.fin(), frame.masked(), 
            payloadSize);

        switch (frame.opcode()) {
            case WebSocketFrame.OPCODE_TEXT -> {
                String text = frame.getPayloadAsText();
                System.out.printf("[Connection %d] Text: %s%n", connectionId, text);
                sendPong();
            }
            case WebSocketFrame.OPCODE_BINARY -> {
                System.out.printf("[Connection %d] Binary payload: %d bytes%n", 
                    connectionId, frame.payload().remaining());
            }
            case WebSocketFrame.OPCODE_PING -> {
                sendPong();
            }
            case WebSocketFrame.OPCODE_CLOSE -> {
                System.out.printf("[Connection %d] Close frame received%n", connectionId);
                channel.close();
            }
        }
    }

    private void sendPong() throws IOException {
        // Send a simple PONG frame: FIN=1, OPCODE=0xA, no mask, no payload
        ByteBuffer pong = ByteBuffer.allocate(2);
        pong.put((byte) 0x8A); // FIN=1, OPCODE=PONG
        pong.put((byte) 0x00); // No mask, length=0
        pong.flip();
        channel.write(pong);
    }

    private void cleanup() {
        try {
            channel.close();
        } catch (IOException e) {
            // Ignore
        }
        metrics.decrementConnections();
        System.out.printf("[Connection %d] Closed%n", connectionId);
    }

    public long getConnectionId() {
        return connectionId;
    }
}
