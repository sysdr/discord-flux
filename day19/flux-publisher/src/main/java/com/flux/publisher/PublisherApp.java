package com.flux.publisher;

import com.flux.publisher.api.DashboardHandler;
import com.flux.publisher.api.MessageApiHandler;
import com.flux.publisher.metrics.MetricsCollector;
import com.flux.publisher.ratelimit.TokenBucketRateLimiter;
import com.flux.publisher.redis.RedisPublisher;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Main application: Publisher API with Virtual Threads.
 * 
 * Architecture:
 * - Virtual Thread per HTTP request (cheap concurrency)
 * - Async Redis publishing (Lettuce + Netty)
 * - Lock-free rate limiting (VarHandle)
 * - Real-time metrics dashboard
 */
public class PublisherApp {
    private static final Logger log = LoggerFactory.getLogger(PublisherApp.class);
    
    private static final int HTTP_PORT = 8080;
    private static final String REDIS_URL = "redis://localhost:6379";
    private static final long MAX_TOKENS = 10_000;
    private static final long REFILL_RATE = 10_000; // 10K msg/sec

    public static void main(String[] args) throws Exception {
        log.info("Starting Flux Publisher...");
        
        // Initialize components
        MetricsCollector metrics = new MetricsCollector();
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(MAX_TOKENS, REFILL_RATE);
        RedisPublisher publisher = new RedisPublisher(REDIS_URL, metrics);
        
        // Create HTTP server with Virtual Thread executor
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        server.setExecutor(executor);
        
        // Register handlers (specific paths first; longest prefix wins)
        server.createContext("/messages", new MessageApiHandler(publisher, rateLimiter, metrics));
        server.createContext("/dashboard", new DashboardHandler(metrics, rateLimiter, publisher));
        server.createContext("/api/metrics", new DashboardHandler(metrics, rateLimiter, publisher));
        server.createContext("/api/reset", exchange -> {
            try {
                if ("POST".equals(exchange.getRequestMethod())) {
                    metrics.reset();
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            } finally {
                exchange.close();
            }
        });
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("/".equals(path) || path.isEmpty()) {
                    exchange.getResponseHeaders().add("Location", "/dashboard");
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            } finally {
                exchange.close();
            }
        });

        // Start server
        server.start();
        
        log.info("Publisher API started on http://localhost:{}", HTTP_PORT);
        log.info("Dashboard available at http://localhost:{}/dashboard", HTTP_PORT);
        log.info("Redis connected: {}", REDIS_URL);
        log.info("Rate limit: {} tokens, {} refill/sec", MAX_TOKENS, REFILL_RATE);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            server.stop(2);
            rateLimiter.shutdown();
            publisher.shutdown();
        }));
    }
}
