package com.flux.gateway.server;

import com.flux.gateway.buffer.ConnectionState;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSocketServer implements Runnable {
    private static final int PORT = 9090;
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final Pattern WS_KEY_PATTERN = Pattern.compile("Sec-WebSocket-Key: (.+)");

    private final ServerSocketChannel serverChannel;
    private final Selector selector;
    private final Map<SocketChannel, ConnectionState> connections;
    private final AtomicLong connectionIdCounter;
    private final AtomicLong broadcastCount;
    private static final int FLUSH_EVERY_N = 800; // Flush every N broadcasts so ring buffer can fill (lag/drops visible on dashboard)
    private volatile boolean running;

    public WebSocketServer() throws IOException {
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.bind(new InetSocketAddress(PORT));

        this.selector = Selector.open();
        this.serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.connections = new ConcurrentHashMap<>();
        this.connectionIdCounter = new AtomicLong(0);
        this.broadcastCount = new AtomicLong(0);
        this.running = true;

        System.out.println("[INFO] WebSocket server listening on port " + PORT);
    }

    @Override
    public void run() {
        while (running) {
            try {
                selector.select(100);

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        handleAccept();
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Selector loop error: " + e.getMessage());
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel client = serverChannel.accept();
        if (client == null) return;

        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);

        String connId = "conn-" + connectionIdCounter.incrementAndGet();
        ConnectionState state = new ConnectionState(connId, client);
        connections.put(client, state);

        System.out.println("[ACCEPT] New connection: " + connId);
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ConnectionState state = connections.get(channel);

        if (state == null) {
            channel.close();
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(4096);
        int bytesRead = channel.read(buffer);

        if (bytesRead == -1) {
            closeConnection(channel, state);
            return;
        }

        buffer.flip();
        String data = StandardCharsets.UTF_8.decode(buffer).toString();

        if (data.startsWith("GET")) {
            handleWebSocketHandshake(channel, data);
        } else {
            handleWebSocketFrame(channel, state, buffer);
        }
    }

    private void handleWebSocketHandshake(SocketChannel channel, String request) throws IOException {
        Matcher matcher = WS_KEY_PATTERN.matcher(request);
        if (!matcher.find()) {
            channel.close();
            return;
        }

        String key = matcher.group(1).trim();
        String accept = generateAcceptKey(key);

        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                         "Upgrade: websocket\r\n" +
                         "Connection: Upgrade\r\n" +
                         "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";

        channel.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
        System.out.println("[HANDSHAKE] Completed for " + connections.get(channel).getConnectionId());
    }

    private String generateAcceptKey(String key) {
        try {
            String concat = key + WS_GUID;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(concat.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleWebSocketFrame(SocketChannel channel, ConnectionState state, ByteBuffer buffer) {
    }

    public void broadcast(String message) {
        byte[] frame = createTextFrame(message);

        for (var entry : connections.entrySet()) {
            ConnectionState state = entry.getValue();
            if (state.isClosed()) continue;

            boolean written = state.tryWrite(frame);
            if (!written) {
                System.out.println("[BACKPRESSURE] Buffer full for " + state.getConnectionId() +
                                 ", lag counter: " + state.getLagCounter());
            }
        }

        // Flush only every N broadcasts so ring buffer can accumulate for slow clients → lag/drops visible on dashboard
        if (broadcastCount.incrementAndGet() % FLUSH_EVERY_N == 0) {
            flushAll();
        }
    }

    private void flushAll() {
        for (var entry : connections.entrySet()) {
            SocketChannel channel = entry.getKey();
            ConnectionState state = entry.getValue();

            if (state.isClosed()) continue;

            byte[] data = state.read(4096);
            if (data.length > 0) {
                try {
                    channel.write(ByteBuffer.wrap(data));
                } catch (IOException e) {
                    System.err.println("[ERROR] Write failed for " + state.getConnectionId());
                    closeConnection(channel, state);
                }
            }
        }
    }

    private byte[] createTextFrame(String message) {
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        int frameSize = 2 + payload.length;

        if (payload.length > 125) {
            frameSize += 2;
        }

        ByteBuffer frame = ByteBuffer.allocate(frameSize);

        frame.put((byte) 0x81);

        if (payload.length <= 125) {
            frame.put((byte) payload.length);
        } else {
            frame.put((byte) 126);
            frame.putShort((short) payload.length);
        }

        frame.put(payload);
        return frame.array();
    }

    private byte[] createCloseFrame(int statusCode) {
        ByteBuffer frame = ByteBuffer.allocate(4);
        frame.put((byte) 0x88);
        frame.put((byte) 2);
        frame.putShort((short) statusCode);
        return frame.array();
    }

    public void forceClose(ConnectionState state) {
        SocketChannel channel = state.getChannel();

        try {
            byte[] closeFrame = createCloseFrame(1008);
            channel.write(ByteBuffer.wrap(closeFrame));
            Thread.sleep(100);
        } catch (Exception e) {
        }

        closeConnection(channel, state);
        System.out.println("[DROP] Forcefully closed " + state.getConnectionId() +
                         " (lag counter: " + state.getLagCounter() + ")");
    }

    private void closeConnection(SocketChannel channel, ConnectionState state) {
        try {
            state.setClosed(true);
            connections.remove(channel);
            channel.close();
        } catch (IOException e) {
            System.err.println("[ERROR] Close failed: " + e.getMessage());
        }
    }

    public Map<SocketChannel, ConnectionState> getConnections() {
        return connections;
    }

    public void shutdown() {
        running = false;
        try {
            selector.close();
            serverChannel.close();
        } catch (IOException e) {
            System.err.println("[ERROR] Shutdown failed: " + e.getMessage());
        }
    }
}
