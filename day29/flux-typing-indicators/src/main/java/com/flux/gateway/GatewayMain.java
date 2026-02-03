package com.flux.gateway;

import com.flux.typing.TypingIndicatorService;
import com.flux.metrics.Metrics;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GatewayMain {
    public static void main(String[] args) throws IOException {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  FLUX TYPING INDICATORS");
        System.out.println("  Ephemeral Events at Scale");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        Metrics metrics = new Metrics();
        TypingIndicatorService typingService = new TypingIndicatorService(metrics);
        
        // Start dashboard
        DashboardServer dashboard = new DashboardServer(8080, typingService, metrics);
        dashboard.start();
        
        // Periodic cleanup task
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
            typingService::periodicCleanup,
            10, 10, TimeUnit.SECONDS
        );
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[INFO] Shutting down...");
            dashboard.stop();
            scheduler.shutdown();
        }));
        
        System.out.println("[INFO] System ready. Press Ctrl+C to shutdown.");
        
        // Simulate background typing activity
        simulateBackgroundActivity(typingService);
    }
    
    private static void simulateBackgroundActivity(TypingIndicatorService service) {
        Thread.ofVirtual().start(() -> {
            Random rand = new Random();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Randomly generate typing events
                    if (rand.nextDouble() < 0.3) { // 30% chance per second
                        long userId = 5000 + rand.nextInt(20);
                        long channelId = 1001;
                        service.handleTypingEvent(userId, channelId);
                    }
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }
}
