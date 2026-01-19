package com.flux.netpoll;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import static org.junit.jupiter.api.Assertions.*;

class ReactorLoopTest {
    
    @Test
    void testReactorAcceptsConnections() throws Exception {
        ReactorLoop reactor = new ReactorLoop(9091);
        Thread reactorThread = Thread.ofPlatform().start(reactor);
        
        Thread.sleep(100); // Let reactor start
        
        // Connect 10 clients
        SocketChannel[] clients = new SocketChannel[10];
        for (int i = 0; i < 10; i++) {
            clients[i] = SocketChannel.open();
            clients[i].connect(new InetSocketAddress("localhost", 9091));
        }
        
        Thread.sleep(200); // Let reactor process
        
        ReactorLoop.Stats stats = reactor.getStats();
        assertEquals(10, stats.activeConnections(), "Should have 10 active connections");
        
        // Cleanup
        for (SocketChannel client : clients) {
            client.close();
        }
        reactor.shutdown();
        reactorThread.join();
    }
    
    @Test
    void testEchoFunctionality() throws Exception {
        ReactorLoop reactor = new ReactorLoop(9092);
        Thread reactorThread = Thread.ofPlatform().start(reactor);
        
        Thread.sleep(100);
        
        SocketChannel client = SocketChannel.open();
        client.connect(new InetSocketAddress("localhost", 9092));
        
        String message = "Hello Reactor!";
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        client.write(buffer);
        
        buffer.clear();
        Thread.sleep(100); // Give reactor time to process
        int bytesRead = client.read(buffer);
        
        assertTrue(bytesRead > 0, "Should receive echo response");
        
        client.close();
        reactor.shutdown();
        reactorThread.join();
    }
}
