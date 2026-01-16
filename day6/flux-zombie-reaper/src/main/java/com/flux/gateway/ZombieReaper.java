package com.flux.gateway;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class ZombieReaper {
    private final TimeoutWheel wheel;
    private final ConnectionRegistry registry;
    private final AtomicLong zombiesKilled;
    private final AtomicBoolean running;
    private Thread reaperThread;
    
    public ZombieReaper(TimeoutWheel wheel, ConnectionRegistry registry) {
        this.wheel = wheel;
        this.registry = registry;
        this.zombiesKilled = new AtomicLong(0);
        this.running = new AtomicBoolean(false);
    }
    
    public void start() {
        if (running.compareAndSet(false, true)) {
            reaperThread = Thread.ofVirtual()
                .name("zombie-reaper")
                .start(this::reapLoop);
            System.out.println("🔪 Zombie Reaper started");
        }
    }
    
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (reaperThread != null) {
                reaperThread.interrupt();
            }
            System.out.println("🛑 Zombie Reaper stopped");
        }
    }
    
    private void reapLoop() {
        while (running.get() && !Thread.interrupted()) {
            try {
                Thread.sleep(Duration.ofSeconds(1));
                
                long startNanos = System.nanoTime();
                Set<String> zombies = wheel.advance();
                
                zombies.forEach(connId -> {
                    registry.get(connId).ifPresent(conn -> {
                        conn.close();
                        registry.remove(connId);
                        zombiesKilled.incrementAndGet();
                        System.out.println("💀 Reaped zombie: " + connId);
                    });
                });
                
                long durationMicros = (System.nanoTime() - startNanos) / 1000;
                
                if (!zombies.isEmpty()) {
                    System.out.printf("⚡ Reaped %d zombies in %d μs%n", 
                        zombies.size(), durationMicros);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    public long getZombiesKilled() {
        return zombiesKilled.get();
    }
    
    public boolean isRunning() {
        return running.get();
    }
}
