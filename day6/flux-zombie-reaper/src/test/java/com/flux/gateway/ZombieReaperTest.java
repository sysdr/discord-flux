package com.flux.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import static org.junit.jupiter.api.Assertions.*;

class ZombieReaperTest {
    private TimeoutWheel wheel;
    private ConnectionRegistry registry;
    private ZombieReaper reaper;
    
    @BeforeEach
    void setUp() {
        wheel = new TimeoutWheel();
        registry = new ConnectionRegistry();
        reaper = new ZombieReaper(wheel, registry);
    }
    
    @Test
    void testReaperStartStop() {
        assertFalse(reaper.isRunning());
        reaper.start();
        assertTrue(reaper.isRunning());
        reaper.stop();
        assertFalse(reaper.isRunning());
    }
    
    @Test
    void testZombieReaping() throws Exception {
        Connection conn = new Connection(SocketChannel.open());
        registry.register(conn);
        wheel.schedule(conn.id(), 2);
        
        reaper.start();
        
        // Wait for reaper to run
        Thread.sleep(3000);
        
        assertEquals(1, reaper.getZombiesKilled());
        assertEquals(0, registry.getActiveCount());
        
        reaper.stop();
    }
}
