package com.flux.gateway.concurrency.nioreactor;

import com.flux.gateway.concurrency.common.ServerInterface;
import com.flux.gateway.concurrency.common.ServerMetrics;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public class NioReactorServer implements ServerInterface {
    private final int port;
    private final ServerMetrics metrics = new ServerMetrics();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private Thread eventLoopThread;
    
    public NioReactorServer(int port) {
        this.port = port;
    }
    
    @Override
    public void start() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        running.set(true);
        
        eventLoopThread = new Thread(() -> {
            System.out.println("[NioReactor] Server started on port " + port);
            try {
                runEventLoop();
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Event loop error: " + e.getMessage());
                }
            }
        }, "NioReactor-EventLoop");
        
        eventLoopThread.start();
    }
    
    private void runEventLoop() throws IOException {
        while (running.get()) {
            int ready = selector.select(1000); // 1 second timeout
            if (!running.get()) break;
            
            if (ready == 0) continue;
            
            Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();
                
                if (!key.isValid()) continue;
                
                try {
                    if (key.isAcceptable()) {
                        acceptConnection(key);
                    } else if (key.isReadable()) {
                        readData(key);
                    }
                } catch (IOException e) {
                    closeConnection(key);
                }
            }
        }
    }
    
    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel client = serverChannel.accept();
        
        if (client != null) {
            client.configureBlocking(false);
            
            // Allocate buffer and attach to key
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            client.register(selector, SelectionKey.OP_READ, buffer);
            
            metrics.connectionAccepted();
        }
    }
    
    private void readData(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            closeConnection(key);
            return;
        }
        
        if (bytesRead > 0) {
            metrics.messageReceived(bytesRead);
            
            // Echo back
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            metrics.messageSent(bytesRead);
            
            buffer.clear();
        }
    }
    
    private void closeConnection(SelectionKey key) {
        try {
            key.channel().close();
            key.cancel();
            metrics.connectionClosed();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    @Override
    public void stop() {
        running.set(false);
        try {
            if (selector != null) {
                selector.wakeup();
                selector.close();
            }
            if (serverChannel != null) serverChannel.close();
            if (eventLoopThread != null) eventLoopThread.join(5000);
        } catch (Exception e) {
            System.err.println("Shutdown error: " + e.getMessage());
        }
    }
    
    @Override
    public void close() {
        stop();
    }
    
    @Override
    public ServerMetrics getMetrics() {
        return metrics;
    }
    
    @Override
    public int getPort() {
        // If port was 0 (random), return the actual bound port
        if (port == 0 && serverChannel != null) {
            try {
                return ((java.net.InetSocketAddress) serverChannel.getLocalAddress()).getPort();
            } catch (IOException e) {
                return port;
            }
        }
        return port;
    }
    
    @Override
    public String getType() {
        return "NioReactor";
    }
}
