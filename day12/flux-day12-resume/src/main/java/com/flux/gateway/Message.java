package com.flux.gateway;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record Message(
    OpCode op,
    long seq,
    String data
) {
    // Serialize to ByteBuffer (binary format for efficient storage)
    public ByteBuffer serialize() {
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + 4 + dataBytes.length);
        buffer.putInt(op.getCode());
        buffer.putLong(seq);
        buffer.putInt(dataBytes.length);
        buffer.put(dataBytes);
        buffer.flip();
        return buffer;
    }
    
    // Deserialize from ByteBuffer
    public static Message deserialize(ByteBuffer buffer) {
        buffer.position(0);
        int opCode = buffer.getInt();
        long seq = buffer.getLong();
        int dataLen = buffer.getInt();
        byte[] dataBytes = new byte[dataLen];
        buffer.get(dataBytes);
        return new Message(
            OpCode.fromCode(opCode),
            seq,
            new String(dataBytes, StandardCharsets.UTF_8)
        );
    }
    
    // Convert to JSON for WebSocket transmission
    public String toJson() {
        return String.format(
            "{\"op\":%d,\"s\":%d,\"d\":%s}",
            op.getCode(),
            seq,
            data.startsWith("{") ? data : "\"" + data + "\""
        );
    }
}
