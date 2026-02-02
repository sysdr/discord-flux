package com.flux.gateway.server;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight HTTP server for dashboard.
 */
public class DashboardServer {
    private static final int PORT = 8080;
    
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        server.createContext("/", exchange -> {
            try {
                String html = Files.readString(Path.of("dashboard/index.html"));
                byte[] response = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (IOException e) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            }
        });
        
        server.createContext("/metrics", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            try {
                String json = Files.readString(Path.of("/tmp/flux-gateway-metrics.json"));
                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (IOException e) {
                String fallback = "{\"connections\":0,\"chunks\":0,\"queue\":0.0,\"rejects\":0}";
                byte[] response = fallback.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        });
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("📊 Dashboard running at http://localhost:" + PORT);
    }
}
