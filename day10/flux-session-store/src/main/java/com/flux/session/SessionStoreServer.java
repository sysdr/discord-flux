package com.flux.session;

import com.sun.net.httpserver.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class SessionStoreServer {
    private static final Logger logger = Logger.getLogger(SessionStoreServer.class.getName());
    private static final AtomicLong sessionIdGenerator = new AtomicLong(1000);
    private static final ProductionSessionStore sessionStore = 
        new ProductionSessionStore(1_000_000, 300); // 5 min idle timeout

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Serve dashboard and handle common browser requests
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/") || path.equals("/dashboard.html")) {
                    serveDashboard(exchange);
                } else if (path.equals("/favicon.ico")) {
                    // Return 204 No Content for favicon requests to avoid 404 errors
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    // For other paths, return 404
                    String response = "404 Not Found";
                    exchange.sendResponseHeaders(404, response.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes(StandardCharsets.UTF_8));
                    }
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        // API: Get metrics
        server.createContext("/api/metrics", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                SessionMetrics metrics = sessionStore.getMetrics();
                String json = metrics.toJson();
                byte[] response = json.getBytes(StandardCharsets.UTF_8);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        // API: Create sessions
        server.createContext("/api/sessions/create", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                int count = Integer.parseInt(body.split("=")[1]);
                
                int created = createSessions(count);
                String response = String.format("{\"created\":%d}", created);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        // API: Mark sessions idle
        server.createContext("/api/sessions/mark-idle", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                int marked = markSessionsIdle();
                String response = String.format("{\"marked\":%d}", marked);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        // API: Manual cleanup
        server.createContext("/api/sessions/cleanup", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                int removed = sessionStore.cleanupStale();
                String response = String.format("{\"removed\":%d}", removed);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        server.start();
        logger.info("SessionStore Dashboard started on http://localhost:" + port);
        logger.info("Press Ctrl+C to stop");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down...");
            sessionStore.shutdown();
            server.stop(0);
        }));
    }

    private static void serveDashboard(HttpExchange exchange) throws IOException {
        // Try multiple possible locations for dashboard.html
        Path[] possiblePaths = {
            Path.of("dashboard.html"),  // Current directory
            Path.of("flux-session-store/dashboard.html"),  // From parent directory
            Path.of(System.getProperty("user.dir"), "dashboard.html"),  // User directory
            Path.of(System.getProperty("user.dir"), "flux-session-store", "dashboard.html"),  // User directory + project
            Path.of(System.getProperty("user.dir")).resolve("dashboard.html"),  // Absolute from user dir
            Path.of(System.getProperty("user.dir")).resolve("flux-session-store").resolve("dashboard.html")  // Absolute project dir
        };
        
        Path dashboardPath = null;
        for (Path path : possiblePaths) {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                dashboardPath = path;
                logger.fine("Found dashboard.html at: " + path.toAbsolutePath());
                break;
            }
        }
        
        if (dashboardPath != null && Files.exists(dashboardPath)) {
            byte[] content = Files.readAllBytes(dashboardPath);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        } else {
            logger.warning("Dashboard not found in any expected location. User dir: " + System.getProperty("user.dir"));
            String fallback = "<html><body><h1>Dashboard not found</h1><p>Please ensure dashboard.html is in the project directory.</p></body></html>";
            byte[] bytes = fallback.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static int createSessions(int count) {
        for (int i = 0; i < count; i++) {
            long sessionId = sessionIdGenerator.getAndIncrement();
            long userId = 1000 + (sessionId % 10000);
            Session session = new Session(
                sessionId,
                userId,
                new InetSocketAddress("127.0.0.1", 50000 + (int)(sessionId % 10000)),
                Instant.now(),
                Instant.now(),
                SessionState.ACTIVE
            );
            sessionStore.addSession(session);
        }
        logger.info("Created " + count + " sessions");
        return count;
    }

    private static int markSessionsIdle() {
        int marked = 0;
        Instant fiveMinutesAgo = Instant.now().minusSeconds(301); // Just past threshold
        
        for (Session session : sessionStore.getAllSessions()) {
            if (marked >= sessionStore.size() / 2) break; // Mark 50%
            
            Session updated = new Session(
                session.sessionId(),
                session.userId(),
                session.remoteAddress(),
                session.connectedAt(),
                fiveMinutesAgo, // Set activity to 5+ minutes ago
                SessionState.IDLE
            );
            sessionStore.updateSession(updated);
            marked++;
        }
        
        logger.info("Marked " + marked + " sessions as idle");
        return marked;
    }
}
