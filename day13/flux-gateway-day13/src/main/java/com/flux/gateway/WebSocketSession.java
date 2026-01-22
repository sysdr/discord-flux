package com.flux.gateway;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSocketSession {
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final Pattern SEC_KEY_PATTERN = Pattern.compile("Sec-WebSocket-Key: (.+)");
    
    private final Socket socket;
    private final String sessionId;
    private long lastSequence = -1;
    private volatile boolean connected = true;
    
    public WebSocketSession(Socket socket, String sessionId) {
        this.socket = socket;
        this.sessionId = sessionId;
    }
    
    public boolean performHandshake() throws IOException {
        byte[] buffer = new byte[4096];
        int read = socket.getInputStream().read(buffer);
        
        if (read <= 0) return false;
        
        String request = new String(buffer, 0, read, StandardCharsets.UTF_8);
        Matcher matcher = SEC_KEY_PATTERN.matcher(request);
        
        if (!matcher.find()) return false;
        
        String key = matcher.group(1).trim();
        String accept = generateAcceptKey(key);
        
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                         "Upgrade: websocket\r\n" +
                         "Connection: Upgrade\r\n" +
                         "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        
        socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
        
        return true;
    }
    
    private String generateAcceptKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            String combined = key + GUID;
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public void sendMessage(byte[] payload) throws IOException {
        if (!connected) return;
        
        ByteBuffer frame = encodeFrame(payload);
        socket.getOutputStream().write(frame.array(), 0, frame.limit());
        socket.getOutputStream().flush();
    }
    
    private ByteBuffer encodeFrame(byte[] payload) {
        int length = payload.length;
        ByteBuffer frame;
        
        if (length <= 125) {
            frame = ByteBuffer.allocate(2 + length);
            frame.put((byte) 0x81); // FIN + Text
            frame.put((byte) length);
        } else if (length <= 65535) {
            frame = ByteBuffer.allocate(4 + length);
            frame.put((byte) 0x81);
            frame.put((byte) 126);
            frame.putShort((short) length);
        } else {
            frame = ByteBuffer.allocate(10 + length);
            frame.put((byte) 0x81);
            frame.put((byte) 127);
            frame.putLong(length);
        }
        
        frame.put(payload);
        frame.flip();
        return frame;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public long getLastSequence() {
        return lastSequence;
    }
    
    public void setLastSequence(long sequence) {
        this.lastSequence = sequence;
    }
    
    public boolean isConnected() {
        return connected && !socket.isClosed();
    }
    
    public void disconnect() {
        connected = false;
        try {
            socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}
