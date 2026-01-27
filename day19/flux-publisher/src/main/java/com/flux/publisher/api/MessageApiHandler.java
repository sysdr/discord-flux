package com.flux.publisher.api;

import com.flux.publisher.Message;
import com.flux.publisher.metrics.MetricsCollector;
import com.flux.publisher.ratelimit.TokenBucketRateLimiter;
import com.flux.publisher.redis.RedisPublisher;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP handler for POST /messages endpoint.
 * Each request runs on a Virtual Thread (cheap concurrency).
 * 
 * Flow:
 * 1. Rate limit check (lock-free token bucket)
 * 2. Parse JSON body
 * 3. Publish to Redis (async)
 * 4. Return 202 Accepted
 */
public class MessageApiHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(MessageApiHandler.class);
    
    private final Gson gson = new Gson();
    private final RedisPublisher publisher;
    private final TokenBucketRateLimiter rateLimiter;
    private final MetricsCollector metrics;

    public MessageApiHandler(RedisPublisher publisher, 
                            TokenBucketRateLimiter rateLimiter,
                            MetricsCollector metrics) {
        this.publisher = publisher;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            // 1. Rate limit check (non-blocking)
            if (!rateLimiter.tryAcquire()) {
                metrics.recordRateLimited();
                sendResponse(exchange, 429, "{\"error\":\"Rate limit exceeded\"}");
                return;
            }

            // 2. Parse request body
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            Message message = new Message(
                json.get("guild_id").getAsString(),
                json.get("channel_id").getAsString(),
                json.get("user_id").getAsString(),
                json.get("content").getAsString(),
                System.currentTimeMillis()
            );

            // 3. Publish async (returns immediately)
            publisher.publish(message)
                .whenComplete((id, ex) -> {
                    try {
                        if (ex != null) {
                            rateLimiter.releaseToken(); // Return token on failure
                            sendResponse(exchange, 503, "{\"error\":\"Redis unavailable\"}");
                        } else {
                            sendResponse(exchange, 202, "{\"id\":\"" + id + "\"}");
                        }
                    } catch (IOException e) {
                        log.error("Failed to send response", e);
                    }
                });

        } catch (Exception e) {
            log.error("Error handling message", e);
            rateLimiter.releaseToken();
            sendResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
