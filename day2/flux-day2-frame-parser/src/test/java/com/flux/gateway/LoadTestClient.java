package com.flux.gateway;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load test client that spawns multiple connections and sends frames.
 */
public class LoadTestClient {

    private static final AtomicLong messagesSent = new AtomicLong(0);

    public static void main(String[] args) throws InterruptedException {
        int numConnections = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int messagesPerConnection = args.length > 1 ? Integer.parseInt(args[1]) : 100;

        System.out.println("🔥 Starting load test:");
        System.out.println("  Connections: " + numConnections);
        System.out.println("  Messages per connection: " + messagesPerConnection);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numConnections; i++) {
            final int clientId = i;
            executor.submit(() -> runClient(clientId, messagesPerConnection));
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        long totalMessages = messagesSent.get();
        double messagesPerSecond = (totalMessages * 1000.0) / duration;

        System.out.println("\n✅ Load test complete:");
        System.out.println("  Total messages: " + totalMessages);
        System.out.println("  Duration: " + duration + "ms");
        System.out.println("  Throughput: " + String.format("%.2f", messagesPerSecond) + " msg/sec");
    }

    private static void runClient(int clientId, int numMessages) {
        try (Socket socket = new Socket("localhost", 9001);
             OutputStream out = socket.getOutputStream()) {

            for (int i = 0; i < numMessages; i++) {
                String message = "Client-" + clientId + "-Msg-" + i;
                sendTextFrame(out, message);
                messagesSent.incrementAndGet();

                // Small delay to simulate realistic traffic
                Thread.sleep(10);
            }

        } catch (Exception e) {
            System.err.println("Client " + clientId + " error: " + e.getMessage());
        }
    }

    private static void sendTextFrame(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes();
        int length = payload.length;

        ByteBuffer frame = ByteBuffer.allocate(2 + 4 + length);
        frame.put((byte) 0x81); // FIN=1, OPCODE=TEXT
        frame.put((byte) (0x80 | length)); // MASKED=1, Length

        // Mask key (just use zeros for test)
        byte[] mask = {0x00, 0x00, 0x00, 0x00};
        frame.put(mask);

        // Payload (already "masked" with zero key)
        frame.put(payload);

        frame.flip();
        out.write(frame.array(), 0, frame.limit());
        out.flush();
    }
}
