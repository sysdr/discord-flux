package com.flux.gateway.websocket;

/**
 * Immutable representation of a parsed WebSocket frame.
 *
 * @param opcode  WebSocket opcode (1=text, 2=binary, 8=close, 9=ping, 10=pong)
 * @param fin     true if this is the final (or only) fragment
 * @param payload unmasked payload bytes
 */
public record WebSocketFrame(int opcode, boolean fin, byte[] payload) {

    // WebSocket opcode constants
    public static final int OPCODE_CONTINUATION = 0x0;
    public static final int OPCODE_TEXT         = 0x1;
    public static final int OPCODE_BINARY       = 0x2;
    public static final int OPCODE_CLOSE        = 0x8;
    public static final int OPCODE_PING         = 0x9;
    public static final int OPCODE_PONG         = 0xA;

    public boolean isText()  { return opcode == OPCODE_TEXT; }
    public boolean isClose() { return opcode == OPCODE_CLOSE; }
    public boolean isPing()  { return opcode == OPCODE_PING; }

    public String textPayload() {
        return new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    }
}
