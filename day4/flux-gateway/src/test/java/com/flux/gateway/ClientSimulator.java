package com.flux.gateway;

import com.flux.gateway.protocol.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates a Gateway client for testing.
 */
public class ClientSimulator implements AutoCloseable {
    
    private final SocketChannel channel;
    private volatile boolean running;
    
    public ClientSimulator(String host, int port) throws IOException {
        this.channel = SocketChannel.open();
        this.channel.connect(new InetSocketAddress(host, port));
        this.running = true;
    }
    
    public void start() {
        Thread.ofVirtual().start(this::receiveLoop);
        Thread.ofVirtual().start(this::heartbeatLoop);
    }
    
    private void receiveLoop() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        
        while (running) {
            try {
                buffer.clear();
                int bytesRead = channel.read(buffer);
                
                if (bytesRead == -1) {
                    running = false;
                    break;
                }
                
                if (bytesRead > 0) {
                    buffer.flip();
                    GatewayPacket packet = GatewayPacket.decode(buffer);
                    handlePacket(packet);
                }
                
                Thread.sleep(10);
            } catch (Exception e) {
                System.err.println("Client error: " + e.getMessage());
                running = false;
            }
        }
    }
    
    private void handlePacket(GatewayPacket packet) {
        switch (packet) {
            case Hello hello -> {
                System.out.println("Received Hello, interval: " + hello.heartbeatInterval());
                // Send Identify
                sendIdentify();
            }
            case HeartbeatAck ack -> {
                // Heartbeat acknowledged
            }
            default -> {
                System.out.println("Received: " + packet.getClass().getSimpleName());
            }
        }
    }
    
    private void sendIdentify() {
        try {
            Identify identify = new Identify("flux_client_" + ThreadLocalRandom.current().nextInt(10000));
            ByteBuffer buffer = identify.encode();
            channel.write(buffer);
            System.out.println("Sent Identify");
        } catch (IOException e) {
            System.err.println("Failed to send Identify: " + e.getMessage());
        }
    }
    
    private void heartbeatLoop() {
        long sequence = 0;
        
        while (running) {
            try {
                Thread.sleep(5000); // Send heartbeat every 5s
                
                Heartbeat hb = new Heartbeat(sequence++);
                ByteBuffer buffer = hb.encode();
                channel.write(buffer);
                
            } catch (Exception e) {
                running = false;
            }
        }
    }
    
    @Override
    public void close() {
        running = false;
        try {
            channel.close();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    public static void main(String[] args) throws Exception {
        try (ClientSimulator client = new ClientSimulator("localhost", 9000)) {
            client.start();
            Thread.sleep(30000); // Run for 30 seconds
        }
    }
}
