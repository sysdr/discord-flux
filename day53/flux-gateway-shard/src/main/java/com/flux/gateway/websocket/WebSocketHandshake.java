package com.flux.gateway.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Performs the RFC 6455 WebSocket HTTP Upgrade handshake.
 *
 * Why hand-roll this?
 *   At scale, every abstraction layer adds latency and allocation.
 *   The entire handshake is ~4 I/O operations (read headers, write 101).
 *   We can do it in under 100 microseconds per connection.
 *   Netty does the same thing — we're just doing it in fewer lines.
 */
public final class WebSocketHandshake {

    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final Pattern KEY_PATTERN =
        Pattern.compile("Sec-WebSocket-Key:\\s*([A-Za-z0-9+/=]+)", Pattern.CASE_INSENSITIVE);

    private WebSocketHandshake() {}

    /**
     * Reads the HTTP GET request, extracts the WebSocket key, and writes
     * the 101 Switching Protocols response.
     *
     * @throws IOException              if the channel fails
     * @throws IllegalArgumentException if the request is not a valid WS upgrade
     */
    public static void perform(InputStream in, OutputStream out) throws IOException {
        var request = readHttpHeaders(in);

        if (!request.contains("Upgrade: websocket") && !request.contains("Upgrade: WebSocket")) {
            throw new IllegalArgumentException("Not a WebSocket upgrade request");
        }

        var matcher = KEY_PATTERN.matcher(request);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing Sec-WebSocket-Key header");
        }

        var clientKey  = matcher.group(1).trim();
        var acceptKey  = computeAcceptKey(clientKey);

        var response = "HTTP/1.1 101 Switching Protocols\r\n" +
                       "Upgrade: websocket\r\n" +
                       "Connection: Upgrade\r\n" +
                       "Sec-WebSocket-Accept: " + acceptKey + "\r\n" +
                       "\r\n";

        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static String readHttpHeaders(InputStream in) throws IOException {
        var sb  = new StringBuilder(512);
        var buf = new byte[1];
        // Read until we see double CRLF (end of HTTP headers)
        while (true) {
            if (in.read(buf) == -1) throw new IOException("Connection closed during handshake");
            sb.append((char) buf[0]);
            if (sb.length() >= 4) {
                var tail = sb.substring(sb.length() - 4);
                if ("\r\n\r\n".equals(tail)) break;
            }
        }
        return sb.toString();
    }

    private static String computeAcceptKey(String clientKey) {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            var combined = (clientKey + WS_GUID).getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(digest.digest(combined));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-1 must be available in all JVMs", e);
        }
    }
}
