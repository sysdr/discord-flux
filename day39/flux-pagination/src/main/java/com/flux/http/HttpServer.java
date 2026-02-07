package com.flux.http;

import com.flux.pagination.CassandraClient;
import com.flux.pagination.PaginationService;
import com.flux.model.PageResult;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class HttpServer {
    
    private final com.sun.net.httpserver.HttpServer server;
    private final PaginationService paginationService;
    private final CassandraClient cassandraClient;
    
    public HttpServer(int port, CassandraClient cassandraClient) throws IOException {
        this.cassandraClient = cassandraClient;
        this.paginationService = new PaginationService(cassandraClient.getSession());
        this.server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        
        setupRoutes();
    }
    
    private void setupRoutes() {
        server.createContext("/messages", this::handleMessages);
        server.createContext("/stats", this::handleStats);
        server.createContext("/dashboard.html", this::handleDashboard);
        server.createContext("/insert", this::handleInsert);
    }
    
    private void handleMessages(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
        
        try {
            long channelId = Long.parseLong(params.getOrDefault("channel_id", "1"));
            String cursor = params.get("cursor");
            int limit = Integer.parseInt(params.getOrDefault("limit", "50"));
            String direction = params.getOrDefault("direction", "next");
            
            PaginationService.Direction dir = "previous".equalsIgnoreCase(direction) 
                ? PaginationService.Direction.PREVIOUS 
                : PaginationService.Direction.NEXT;
            
            PageResult result = paginationService.fetchPage(channelId, cursor, limit, dir);
            
            sendJsonResponse(exchange, 200, result.toJson());
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "Invalid parameters");
        }
    }
    
    private void handleStats(HttpExchange exchange) throws IOException {
        String json = String.format(
            "{\"totalQueries\":%d,\"averageLatencyMs\":%.2f}",
            paginationService.getTotalQueries(),
            paginationService.getAverageLatencyMs()
        );
        sendJsonResponse(exchange, 200, json);
    }
    
    private void handleInsert(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }
        
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = parseQueryParams(body);
        
        try {
            long channelId = Long.parseLong(params.getOrDefault("channel_id", "1"));
            long authorId = Long.parseLong(params.getOrDefault("author_id", "1000"));
            String content = params.getOrDefault("content", "Test message");
            
            var message = cassandraClient.insertMessage(channelId, authorId, content);
            
            sendJsonResponse(exchange, 201, message.toJson());
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error: " + e.getMessage());
        }
    }
    
    private void handleDashboard(HttpExchange exchange) throws IOException {
        try {
            java.nio.file.Path dashboardPath = java.nio.file.Paths.get(System.getProperty("user.dir"), "dashboard.html");
            if (!java.nio.file.Files.exists(dashboardPath)) {
                dashboardPath = java.nio.file.Paths.get("dashboard.html");
            }
            String html = Files.readString(dashboardPath);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } catch (Exception e) {
            sendResponse(exchange, 404, "Dashboard not found");
        } finally {
            exchange.close();
        }
    }
    
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                params.put(
                    URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                );
            }
        }
        return params;
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    
    public void start() {
        server.start();
        System.out.println("🌐 HTTP Server started on port " + server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
        System.out.println("🛑 HTTP Server stopped");
    }
}
