package com.flux.migration;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight HTTP server for real-time migration dashboard.
 * Serves static HTML and provides metrics endpoint.
 */
public class DashboardServer {
    
    private static final Logger log = LoggerFactory.getLogger(DashboardServer.class);
    
    private final HttpServer server;
    
    public DashboardServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Favicon: return 204 No Content so browser does not 404 (register before "/")
        server.createContext("/favicon.ico", exchange -> {
            exchange.sendResponseHeaders(204, 0);
            exchange.close();
        });
        
        // Serve dashboard HTML (register before "/" so path matches correctly)
        server.createContext("/dashboard", exchange -> {
            byte[] response = Files.readAllBytes(Path.of("dashboard/index.html"));
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        
        // Metrics endpoint (JSON) - read from file written by MigrationOrchestrator
        server.createContext("/metrics", exchange -> {
            Path metricsPath = Path.of("metrics.json");
            String json;
            if (Files.exists(metricsPath)) {
                try {
                    json = Files.readString(metricsPath);
                } catch (IOException e) {
                    json = "{\"parsedMessages\":0,\"writtenMessages\":0,\"throughput\":0,\"heapUsedMB\":0,\"virtualThreads\":1000}";
                }
            } else {
                json = "{\"parsedMessages\":0,\"writtenMessages\":0,\"throughput\":0,\"heapUsedMB\":0,\"virtualThreads\":1000}";
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        
        // Root: redirect to dashboard
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path)) {
                exchange.getResponseHeaders().set("Location", "/dashboard");
                exchange.sendResponseHeaders(302, 0);
            } else {
                exchange.sendResponseHeaders(404, 0);
            }
            exchange.close();
        });
        
        server.setExecutor(null); // Default executor
        log.info("Dashboard server initialized on port {}", port);
    }
    
    public void start() {
        server.start();
        log.info("Dashboard available at http://localhost:{}/dashboard", server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
    }
    
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        DashboardServer dashboard = new DashboardServer(port);
        dashboard.start();
        Runtime.getRuntime().addShutdownHook(new Thread(dashboard::stop));
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
