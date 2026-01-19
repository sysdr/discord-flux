package com.flux.netpoll;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class ReactorLoop implements Runnable {
    private final Selector selector;
    private final ServerSocketChannel serverChannel;
    private final ConnectionRegistry registry;
    private final EventDispatcher dispatcher;
    private final BufferPool bufferPool;
    private volatile boolean running = true;
    
    private final AtomicLong wakeCount = new AtomicLong(0);
    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final ConcurrentLinkedQueue<SelectionKey> pendingCloses = new ConcurrentLinkedQueue<>();

    public ReactorLoop(int port) throws IOException {
        this.selector = Selector.open();
        this.serverChannel = ServerSocketChannel.open();
        this.registry = new ConnectionRegistry();
        this.dispatcher = new EventDispatcher();
        this.bufferPool = new BufferPool(1000);
        
        serverChannel.configureBlocking(false);
        // Bind to all interfaces (0.0.0.0) for WSL2 and network access
        serverChannel.bind(new InetSocketAddress("0.0.0.0", port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        System.out.println("✓ Reactor listening on port " + port);
    }

    @Override
    public void run() {
        System.out.println("✓ Reactor loop started");
        
        while (running) {
            try {
                // Process pending closes first (from virtual threads)
                processPendingCloses();
                
                int readyCount = selector.select(100); // 100ms timeout
                wakeCount.incrementAndGet();
                
                if (readyCount == 0) {
                    continue;
                }
                
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    
                    if (!key.isValid()) {
                        continue;
                    }
                    
                    try {
                        if (key.isAcceptable()) {
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            handleRead(key);
                        }
                        eventsProcessed.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Error processing key: " + e.getMessage());
                        closeChannel(key);
                    }
                }
                
            } catch (IOException e) {
                System.err.println("Selector error: " + e.getMessage());
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        
        if (clientChannel == null) {
            return;
        }
        
        clientChannel.configureBlocking(false);
        
        ChannelHandler handler = new ChannelHandler(
            registry.nextConnectionId(),
            clientChannel,
            bufferPool
        );
        
        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ);
        clientKey.attach(handler);
        
        registry.register(handler);
        
        System.out.println("✓ Accepted connection: " + handler.connectionId() + 
                         " (Total: " + registry.activeCount() + ")");
    }

    private void handleRead(SelectionKey key) {
        ChannelHandler handler = (ChannelHandler) key.attachment();
        
        // Dispatch to Virtual Thread for non-blocking processing
        dispatcher.dispatch(() -> {
            try {
                boolean keepAlive = handler.handleRead();
                if (!keepAlive) {
                    scheduleClose(key);
                }
            } catch (IOException e) {
                System.err.println("Read error on connection " + 
                                 handler.connectionId() + ": " + e.getMessage());
                scheduleClose(key);
            }
        });
    }

    private void scheduleClose(SelectionKey key) {
        // Queue close operation to be handled on reactor thread
        pendingCloses.offer(key);
        selector.wakeup(); // Wake up selector to process the close
    }

    private void processPendingCloses() {
        SelectionKey key;
        while ((key = pendingCloses.poll()) != null) {
            closeChannel(key);
        }
    }

    private void closeChannel(SelectionKey key) {
        try {
            ChannelHandler handler = (ChannelHandler) key.attachment();
            if (handler != null) {
                long connId = handler.connectionId();
                registry.unregister(connId);
                System.out.println("✗ Closed connection: " + connId + 
                                 " (Total: " + registry.activeCount() + ")");
            }
            if (key.channel().isOpen()) {
                key.channel().close();
            }
            if (key.isValid()) {
                key.cancel();
            }
        } catch (IOException e) {
            System.err.println("Error closing channel: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        try {
            selector.close();
            serverChannel.close();
            dispatcher.shutdown();
        } catch (IOException e) {
            System.err.println("Shutdown error: " + e.getMessage());
        }
    }

    public Stats getStats() {
        return new Stats(
            wakeCount.get(),
            eventsProcessed.get(),
            registry.activeCount(),
            dispatcher.getActiveThreads()
        );
    }

    public record Stats(long wakeCount, long eventsProcessed, 
                       int activeConnections, int activeThreads) {}
}
