package com.flux.gateway;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LoadTest {
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Starting Load Test...");
        
        TimeoutWheel wheel = new TimeoutWheel();
        ConnectionRegistry registry = new ConnectionRegistry();
        ZombieReaper reaper = new ZombieReaper(wheel, registry);
        
        int totalConnections = 10000;
        int zombieConnections = 1000;
        
        System.out.println("📊 Spawning " + totalConnections + " connections...");
        
        List<Connection> connections = new ArrayList<>();
        for (int i = 0; i < totalConnections; i++) {
            Connection conn = new Connection(SocketChannel.open());
            registry.register(conn);
            connections.add(conn);
            
            // Schedule all connections for 30-second timeout
            wheel.schedule(conn.id(), 30);
        }
        
        System.out.println("✅ Spawned " + totalConnections + " connections");
        System.out.println("🔪 Starting reaper...");
        
        reaper.start();
        
        // Simulate heartbeats for non-zombie connections
        Thread.ofVirtual().start(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    
                    // Reschedule only non-zombie connections
                    for (int i = zombieConnections; i < connections.size(); i++) {
                        Connection conn = connections.get(i);
                        if (conn.isOpen()) {
                            conn.updateLastHeartbeat();
                            wheel.schedule(conn.id(), 30);
                        }
                    }
                    
                    System.out.println("💓 Sent heartbeats for " + (totalConnections - zombieConnections) + " connections");
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        
        System.out.println("⏳ Waiting 35 seconds for zombies to be reaped...");
        Thread.sleep(35000);
        
        long killed = reaper.getZombiesKilled();
        int remaining = registry.getActiveCount();
        
        System.out.println("\n📊 Load Test Results:");
        System.out.println("   Total Connections: " + totalConnections);
        System.out.println("   Zombies Killed: " + killed);
        System.out.println("   Remaining Active: " + remaining);
        System.out.println("   Expected Zombies: " + zombieConnections);
        
        if (killed >= zombieConnections * 0.95) {
            System.out.println("✅ Load test PASSED");
        } else {
            System.out.println("❌ Load test FAILED");
        }
        
        reaper.stop();
        System.exit(0);
    }
}
