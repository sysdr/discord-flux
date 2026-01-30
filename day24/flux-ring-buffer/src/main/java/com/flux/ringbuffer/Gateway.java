package com.flux.ringbuffer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gateway that manages client connections and event broadcasting.
 */
public class Gateway {
    private final List<ClientConnection> clients = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final int bufferCapacity;
    
    private static final int BACKPRESSURE_THRESHOLD_PERCENT = 80;
    private static final long SLOW_CLIENT_DISCONNECT_NANOS = TimeUnit.SECONDS.toNanos(5);
    
    public Gateway(int bufferCapacity) {
        this.bufferCapacity = bufferCapacity;
        
        // Start I/O flush loop (simulates async socket writes)
        scheduler.scheduleAtFixedRate(this::flushAllClients, 0, 10, TimeUnit.MILLISECONDS);
        
        // Start backpressure monitor
        scheduler.scheduleAtFixedRate(this::monitorBackpressure, 0, 1, TimeUnit.SECONDS);
    }
    
    public void addClient(ClientConnection client) {
        clients.add(client);
    }
    
    public void removeClient(String clientId) {
        clients.removeIf(c -> c.getClientId().equals(clientId));
    }
    
    /**
     * Broadcast an event to all connected clients.
     */
    public void broadcast(GuildEvent event) {
        eventsProcessed.incrementAndGet();
        
        for (ClientConnection client : clients) {
            if (!client.isConnected()) {
                continue;
            }
            
            boolean success = client.enqueue(event);
            if (!success) {
                handleBackpressure(client, event);
            }
        }
    }
    
    private void handleBackpressure(ClientConnection client, GuildEvent event) {
        // Log backpressure; keep clients connected so dashboard metrics stay visible
        if (client.getBufferUtilization() >= 95) {
            System.out.printf("[BACKPRESSURE] Client %s buffer at %d%% (dropping event)%n", 
                client.getClientId(), client.getBufferUtilization());
        }
    }
    
    private void flushAllClients() {
        for (ClientConnection client : clients) {
            if (client.isConnected()) {
                try {
                    client.flush();
                } catch (Exception e) {
                    System.err.printf("Error flushing client %s: %s%n", 
                        client.getClientId(), e.getMessage());
                }
            }
        }
    }
    
    private void monitorBackpressure() {
        int highUtilization = 0;
        int totalBackpressure = 0;
        
        for (ClientConnection client : clients) {
            int utilization = client.getBufferUtilization();
            if (utilization >= BACKPRESSURE_THRESHOLD_PERCENT) {
                highUtilization++;
            }
            totalBackpressure += client.getBackpressureEvents();
        }
        
        if (highUtilization > 0) {
            System.out.printf("[MONITOR] %d clients above %d%% utilization, total backpressure events: %d%n",
                highUtilization, BACKPRESSURE_THRESHOLD_PERCENT, totalBackpressure);
        }
    }
    
    public List<ClientConnection> getClients() {
        return List.copyOf(clients);
    }
    
    public long getEventsProcessed() {
        return eventsProcessed.get();
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
