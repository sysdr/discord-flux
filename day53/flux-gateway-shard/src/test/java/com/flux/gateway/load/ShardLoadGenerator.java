package com.flux.gateway.load;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

/**
 * Virtual-thread load generator for the Flux Gateway.
 *
 * Each virtual thread acts as one bot shard:
 *   1. Opens a TCP connection to localhost:8888
 *   2. Performs the WebSocket HTTP upgrade
 *   3. Reads the HELLO frame (op=10)
 *   4. Sends IDENTIFY with its assigned [shardId, numShards]
 *   5. Reads the READY frame (op=0) or INVALID_SESSION (op=9)
 *   6. Keeps the connection alive by sending heartbeats
 *
 * Usage:
 *   java -cp target/... com.flux.gateway.load.ShardLoadGenerator [numShards] [mode]
 *   mode: normal | zombie | conflict
 */
public final class ShardLoadGenerator {

    private static final String HOST     = "localhost";
    private static final int    PORT     = 8888;
    private static final String WS_GUID  = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final LongAdder readyCount     = new LongAdder();
    private static final LongAdder rejectedCount  = new LongAdder();
    private static final LongAdder errorCount     = new LongAdder();

    public static void main(String[] args) throws Exception {
        int    numShards   = args.length > 0 ? Integer.parseInt(args[0]) : 16;
        String mode        = args.length > 1 ? args[1] : "normal";
        int    holdSeconds = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        System.out.printf("[LoadGen] Starting in mode=%s with %d shards (hold=%ds)%n", mode, numShards, holdSeconds);
        long start = System.currentTimeMillis();

        switch (mode) {
            case "normal"   -> runNormal(numShards, holdSeconds);
            case "zombie"   -> runZombie(numShards);
            case "conflict" -> runConflict(numShards);
            default         -> { System.err.println("Unknown mode: " + mode); System.exit(1); }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("""
            [LoadGen] Results:
              READY:    %d
              REJECTED: %d
              ERRORS:   %d
              Time:     %dms
            %n""", readyCount.sum(), rejectedCount.sum(), errorCount.sum(), elapsed);
    }

    // ── Scenario: Connect all shards normally ─────────────────────────────

    private static void runNormal(int numShards, int holdSeconds) throws InterruptedException {
        var latch = new CountDownLatch(numShards);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numShards; i++) {
                final int shardId = i;
                executor.submit(() -> {
                    try {
                        connectShard(shardId, numShards, "Bot flux.shard." + shardId + ".token123456", holdSeconds <= 0, holdSeconds);
                    } catch (Exception e) {
                        errorCount.increment();
                        System.err.printf("[Shard %d] Error: %s%n", shardId, e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        latch.await();
    }

    // ── Scenario: Zombie eviction — kill shard 3, reconnect it ────────────

    private static void runZombie(int numShards) throws Exception {
        System.out.println("[LoadGen] Phase 1: Connecting all shards...");
        runNormal(numShards, 0);
        Thread.sleep(500);

        // Shard 3's first connection is already closed (runNormal keeps alive briefly)
        // In zombie mode, we forcibly disconnect shard 3 by connecting again without cleanup
        System.out.println("[LoadGen] Phase 2: Reconnecting shard 3 (zombie eviction scenario)...");
        try {
            connectShard(3, numShards, "Bot flux.shard.3.token123456", false, 0);
        } catch (Exception e) {
            System.out.printf("[Zombie] Shard 3 reconnect result: %s%n", e.getMessage());
        }
        System.out.println("[LoadGen] Zombie scenario complete. Check dashboard for ZOMBIE_EVICTED log.");
    }

    // ── Scenario: Conflict — two clients claim same shard slot ────────────

    private static void runConflict(int numShards) throws Exception {
        var latch = new CountDownLatch(2);
        System.out.println("[LoadGen] Sending TWO IDENTIFY for [5, 16]...");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        connectShard(5, numShards, "Bot flux.shard.5.token123456", false, 0);
                    } catch (Exception e) {
                        System.out.printf("[Conflict] Result: %s%n", e.getMessage());
                    } finally { latch.countDown(); }
                });
            }
        }
        latch.await();
        System.out.printf("[LoadGen] Conflict result — READY: %d, REJECTED: %d%n",
            readyCount.sum(), rejectedCount.sum());
    }

    // ── Core connection routine ───────────────────────────────────────────

    private static void connectShard(int shardId, int numShards, String token, boolean keepAlive, int holdSeconds)
            throws Exception {
        try (var socket = new Socket(HOST, PORT)) {
            socket.setSoTimeout(5000);
            var in  = socket.getInputStream();
            var out = socket.getOutputStream();

            // 1. WebSocket handshake
            var key = Base64.getEncoder().encodeToString(
                ("flux-shard-" + shardId + new Random().nextInt()).getBytes());
            var request = "GET / HTTP/1.1\r\n" +
                "Host: localhost:" + PORT + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + key + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 2. Read 101 response
            readHttpResponse(in);

            // 3. Read HELLO (op=10) frame
            var hello = readUnmaskedTextFrame(in);
            if (!hello.contains("\"op\":10")) {
                throw new IllegalStateException("Expected HELLO, got: " + hello);
            }

            // 4. Send IDENTIFY with shard array
            var identify = """
                {"op":2,"d":{"token":"%s","intents":513,"shard":[%d,%d],\
                "properties":{"os":"linux","browser":"flux","device":"flux"}}}
                """.formatted(token, shardId, numShards).strip();
            writeMaskedTextFrame(out, identify);

            // 5. Read READY or INVALID_SESSION
            var response = readUnmaskedTextFrame(in);
            if (response.contains("\"op\":0")) {
                readyCount.increment();
                System.out.printf("[LoadGen] Shard [%d,%d] READY%n", shardId, numShards);
                if (holdSeconds > 0) {
                    // Keep connection open so dashboard can display (heartbeat every ~4s)
                    for (int remaining = holdSeconds; remaining > 0; remaining -= 4) {
                        Thread.sleep(Math.min(4000, remaining * 1000L));
                        writeMaskedTextFrame(out, "{\"op\":1,\"d\":null}");
                        readUnmaskedTextFrame(in);
                    }
                } else if (keepAlive) {
                    Thread.sleep(200);
                    writeMaskedTextFrame(out, "{\"op\":1,\"d\":null}");
                    readUnmaskedTextFrame(in);
                }
            } else if (response.contains("\"op\":9")) {
                rejectedCount.increment();
                System.out.printf("[LoadGen] Shard [%d,%d] INVALID_SESSION: %s%n",
                    shardId, numShards, response);
            } else {
                System.out.printf("[LoadGen] Shard [%d,%d] unexpected: %s%n",
                    shardId, numShards, response);
            }
        }
    }

    // ── Minimal WebSocket frame I/O ───────────────────────────────────────

    private static void writeMaskedTextFrame(OutputStream out, String text) throws Exception {
        byte[] payload  = text.getBytes(StandardCharsets.UTF_8);
        byte[] maskKey  = {(byte)0x37, (byte)0xfa, (byte)0x21, (byte)0x3d};
        byte[] masked   = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) masked[i] = (byte)(payload[i] ^ maskKey[i % 4]);

        int len = payload.length;
        out.write(0x81); // FIN + text
        if (len <= 125) {
            out.write(0x80 | len); // MASK bit + len
        } else {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        }
        out.write(maskKey);
        out.write(masked);
        out.flush();
    }

    private static String readUnmaskedTextFrame(InputStream in) throws Exception {
        int b0 = in.read(); if (b0 < 0) throw new IOException("EOF");
        int b1 = in.read(); if (b1 < 0) throw new IOException("EOF");
        int len = b1 & 0x7F;
        if (len == 126) {
            len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        }
        byte[] payload = in.readNBytes(len);
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static void readHttpResponse(InputStream in) throws Exception {
        var sb  = new StringBuilder();
        var buf = new byte[1];
        while (true) {
            in.read(buf);
            sb.append((char) buf[0]);
            if (sb.length() >= 4 && sb.substring(sb.length()-4).equals("\r\n\r\n")) break;
        }
        if (!sb.toString().contains("101")) {
            throw new IllegalStateException("Unexpected HTTP response: " + sb.substring(0, 50));
        }
    }
}
