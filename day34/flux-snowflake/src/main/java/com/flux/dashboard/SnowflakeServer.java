package com.flux.dashboard;

import com.flux.snowflake.SnowflakeGenerator;
import com.flux.snowflake.SnowflakeId;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * HTTP server exposing Snowflake ID generation API and dashboard.
 */
public class SnowflakeServer {
    
    private static final int PORT = 8080;
    private static final int WORKER_ID = 1; // Default worker
    
    private final SnowflakeGenerator generator;
    private final HttpServer server;
    private final MetricsCollector metrics;
    
    public SnowflakeServer() throws IOException {
        this.generator = new SnowflakeGenerator(WORKER_ID);
        this.metrics = new MetricsCollector();
        this.server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Use Virtual Threads for request handling
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        
        // Register endpoints
        server.createContext("/api/id", this::handleGenerateId);
        server.createContext("/api/parse", this::handleParseId);
        server.createContext("/api/metrics", this::handleMetrics);
        server.createContext("/dashboard", this::handleDashboard);
        server.createContext("/", this::handleRoot);
    }
    
    public void start() {
        server.start();
        System.out.println("🚀 Snowflake Server running on http://localhost:" + PORT);
        System.out.println("📊 Dashboard: http://localhost:" + PORT + "/dashboard");
    }
    
    public void stop() {
        server.stop(0);
    }
    
    private void handleGenerateId(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        long startNanos = System.nanoTime();
        long id = generator.nextId();
        long latencyNanos = System.nanoTime() - startNanos;
        
        metrics.recordIdGeneration(latencyNanos);
        
        String response = String.format("{\"id\": %d, \"latency_ns\": %d}", id, latencyNanos);
        sendJsonResponse(exchange, 200, response);
    }
    
    private void handleParseId(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        String query = exchange.getRequestURI().getQuery();
        if (query == null || !query.startsWith("id=")) {
            sendResponse(exchange, 400, "Missing 'id' query parameter");
            return;
        }
        
        try {
            long id = Long.parseLong(query.substring(3));
            SnowflakeId parsed = SnowflakeGenerator.parse(id);
            
            String response = String.format(
                "{\"id\": %d, \"timestamp\": %d, \"worker_id\": %d, \"sequence\": %d, \"instant\": \"%s\"}",
                parsed.id(), parsed.timestamp(), parsed.workerId(), parsed.sequence(), parsed.toInstant()
            );
            sendJsonResponse(exchange, 200, response);
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "Invalid ID format");
        }
    }
    
    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        String response = String.format("""
            {
                "ids_generated": %d,
                "throughput_per_sec": %.2f,
                "clock_drift_events": %d,
                "sequence_exhaustion_events": %d,
                "avg_latency_ns": %.2f,
                "p99_latency_ns": %.2f
            }
            """,
            metrics.getTotalIds(),
            metrics.getThroughput(),
            generator.getClockDriftEvents(),
            generator.getSequenceExhaustionEvents(),
            metrics.getAvgLatency(),
            metrics.getP99Latency()
        );
        
        sendJsonResponse(exchange, 200, response);
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        String html = DashboardHtml.getHtml();
        sendHtmlResponse(exchange, 200, html);
    }
    
    private void handleRoot(HttpExchange exchange) throws IOException {
        String response = """
            Snowflake ID Generator
            
            Endpoints:
            - GET  /api/id         - Generate new ID
            - GET  /api/parse?id=X - Parse ID components
            - GET  /api/metrics    - Get metrics
            - GET  /dashboard      - Web dashboard
            """;
        sendResponse(exchange, 200, response);
    }
    
    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    private void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(exchange, status, json);
    }
    
    private void sendHtmlResponse(HttpExchange exchange, int status, String html) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        sendResponse(exchange, status, html);
    }
    
    public static void main(String[] args) {
        try {
            SnowflakeServer server = new SnowflakeServer();
            server.start();
            
            // Keep server alive
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
