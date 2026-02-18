package com.flux.gateway.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * RFC 6455 WebSocket frame parser and serializer.
 *
 * Frame wire format:
 *   Byte 0: FIN(1) RSV1-3(3) OPCODE(4)
 *   Byte 1: MASK(1) PAYLOAD_LEN(7)
 *   Bytes 2-3 or 2-9: Extended payload length (if len==126 or 127)
 *   Bytes n to n+3: Masking key (4 bytes, present if MASK==1)
 *   Remaining: Masked payload
 *
 * CRITICAL: Client-to-server frames are ALWAYS masked (RFC 6455 §5.1).
 *           Server-to-client frames are NEVER masked.
 *           Failing to unmask input produces garbage JSON. Masking output
 *           causes clients to refuse the frames.
 */
public final class WebSocketFrameParser {

    private static final int MAX_FRAME_SIZE = 64 * 1024; // 64 KB per frame

    private WebSocketFrameParser() {}

    /**
     * Reads and parses one complete WebSocket frame from the input stream.
     * Blocks until the full frame is available.
     *
     * @throws IOException              on channel error
     * @throws IllegalStateException    if frame exceeds MAX_FRAME_SIZE
     * @throws IllegalArgumentException if frame violates RFC 6455
     */
    public static WebSocketFrame readFrame(InputStream in) throws IOException {
        // Byte 0: FIN + opcode
        int byte0 = readByte(in);
        boolean fin    = (byte0 & 0x80) != 0;
        int     opcode = (byte0 & 0x0F);

        // Byte 1: MASK flag + initial payload length
        int byte1   = readByte(in);
        boolean masked     = (byte1 & 0x80) != 0;
        long    payloadLen = (byte1 & 0x7F);

        // Extended payload length
        if (payloadLen == 126) {
            payloadLen = ((readByte(in) & 0xFF) << 8) | (readByte(in) & 0xFF);
        } else if (payloadLen == 127) {
            payloadLen = 0;
            for (int i = 0; i < 8; i++) {
                payloadLen = (payloadLen << 8) | (readByte(in) & 0xFF);
            }
        }

        if (payloadLen > MAX_FRAME_SIZE) {
            throw new IllegalStateException(
                "Frame payload %d bytes exceeds maximum %d".formatted(payloadLen, MAX_FRAME_SIZE));
        }

        // Masking key (4 bytes if MASK bit set)
        byte[] maskKey = new byte[4];
        if (masked) {
            readFully(in, maskKey, 4);
        }

        // Payload
        byte[] payload = new byte[(int) payloadLen];
        readFully(in, payload, (int) payloadLen);

        // Unmask payload (XOR with cycling mask key)
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }

        return new WebSocketFrame(opcode, fin, payload);
    }

    /**
     * Serializes and writes a text WebSocket frame (unmasked — server-to-client).
     */
    public static void writeTextFrame(OutputStream out, String text) throws IOException {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        writeFrame(out, WebSocketFrame.OPCODE_TEXT, payload);
    }

    /**
     * Serializes and writes a CLOSE frame with the given status code.
     */
    public static void writeCloseFrame(OutputStream out, int statusCode) throws IOException {
        byte[] payload = new byte[]{
            (byte) ((statusCode >> 8) & 0xFF),
            (byte) (statusCode & 0xFF)
        };
        writeFrame(out, WebSocketFrame.OPCODE_CLOSE, payload);
    }

    // ── Private ───────────────────────────────────────────────────────────

    private static void writeFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        int len = payload.length;
        // Byte 0: FIN=1, RSV=0, opcode
        out.write(0x80 | opcode);
        // Byte 1+: payload length (unmasked — server side)
        if (len <= 125) {
            out.write(len);
        } else if (len <= 65535) {
            out.write(126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(127);
            for (int i = 7; i >= 0; i--) {
                out.write((len >> (8 * i)) & 0xFF);
            }
        }
        out.write(payload);
        out.flush();
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b == -1) throw new IOException("End of stream");
        return b;
    }

    private static void readFully(InputStream in, byte[] buf, int len) throws IOException {
        int read = 0;
        while (read < len) {
            int n = in.read(buf, read, len - read);
            if (n == -1) throw new IOException("End of stream reading frame");
            read += n;
        }
    }
}
