package com.flux.grpc;

import com.sun.net.httpserver.HttpServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GrpcServer {
    private Server server;
    private HttpServer metricsServer;
    
    public void start() throws IOException {
        int grpcPort = 9090;
        
        server = ServerBuilder.forPort(grpcPort)
            .addService(new MessageServiceImpl())
            .addService(ProtoReflectionService.newInstance())
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build()
            .start();
        
        System.out.println("✅ gRPC Server started on port " + grpcPort);
        System.out.println("📊 Server Reflection enabled (use grpcurl to introspect)");
        
        startMetricsServer();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down gRPC server...");
            try {
                GrpcServer.this.stop();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
    }
    
    private void startMetricsServer() throws IOException {
        metricsServer = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // Run demo endpoint - triggers load test to populate metrics
        metricsServer.createContext("/run-demo", exchange -> {
            if ("GET".equals(exchange.getRequestMethod()) || "POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                new Thread(() -> {
                    try {
                        LoadTestClient client = new LoadTestClient("localhost", 9090);
                        client.runLoadTest(100, 20);
                        client.shutdown();
                    } catch (Exception e) {
                        System.err.println("[Demo] Error: " + e.getMessage());
                    }
                }).start();
                String msg = "Demo started - metrics will update shortly";
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, msg.length());
                exchange.getResponseBody().write(msg.getBytes());
            } else {
                exchange.sendResponseHeaders(405, 0);
            }
            exchange.close();
        });
        
        // Metrics endpoint
        metricsServer.createContext("/metrics", exchange -> {
            ScyllaDBClient.getSession(); // Ensure connection attempt for dashboard status
            String json = MetricsCollector.getMetricsJson();
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, jsonBytes.length);
            exchange.getResponseBody().write(jsonBytes);
            exchange.close();
        });
        
        // Dashboard
        metricsServer.createContext("/", exchange -> {
            try {
                Path dashboardPath = Path.of("dashboard/index.html");
                byte[] htmlBytes = Files.readAllBytes(dashboardPath);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, htmlBytes.length);
                exchange.getResponseBody().write(htmlBytes);
                exchange.close();
            } catch (Exception e) {
                String error = "Dashboard not found";
                byte[] errBytes = error.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, errBytes.length);
                exchange.getResponseBody().write(errBytes);
                exchange.close();
            }
        });
        
        metricsServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        metricsServer.start();
        
        System.out.println("📈 Metrics server started: http://localhost:8080");
        System.out.println("🎯 Dashboard: http://localhost:8080/");
    }
    
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (metricsServer != null) {
            metricsServer.stop(0);
        }
        ScyllaDBClient.shutdown();
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public static void main(String[] args) throws Exception {
        final GrpcServer server = new GrpcServer();
        server.start();
        server.blockUntilShutdown();
    }
}
