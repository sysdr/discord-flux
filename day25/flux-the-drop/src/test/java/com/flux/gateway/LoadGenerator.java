package com.flux.gateway;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class LoadGenerator {
    private static final String WS_URL = "ws://localhost:9090";

    public static void main(String[] args) throws Exception {
        int normalClients = Integer.parseInt(args.length > 0 ? args[0] : "70");
        int slowClients = Integer.parseInt(args.length > 1 ? args[1] : "30");

        System.out.println("Spawning " + normalClients + " normal clients + " + slowClients + " slow clients...");

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        List<WebSocket> sockets = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(normalClients + slowClients);

        for (int i = 0; i < normalClients; i++) {
            spawnClient(httpClient, "normal-" + i, false, sockets, latch);
        }

        for (int i = 0; i < slowClients; i++) {
            spawnClient(httpClient, "slow-" + i, true, sockets, latch);
        }

        latch.await();
        System.out.println("All clients connected!");

        Thread.sleep(300000);
    }

    private static void spawnClient(HttpClient httpClient, String id, boolean slow,
                                   List<WebSocket> sockets, CountDownLatch latch) {
        CompletableFuture<WebSocket> ws = httpClient.newWebSocketBuilder()
            .buildAsync(URI.create(WS_URL), new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    System.out.println("[" + id + "] Connected");
                    latch.countDown();
                    webSocket.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    if (slow) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    webSocket.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    System.out.println("[" + id + "] Closed: " + statusCode + " - " + reason);
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    System.err.println("[" + id + "] Error: " + error.getMessage());
                }
            });

        ws.thenAccept(sockets::add);
    }
}
