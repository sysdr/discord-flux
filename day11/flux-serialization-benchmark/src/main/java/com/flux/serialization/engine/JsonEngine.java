package com.flux.serialization.engine;

import com.flux.serialization.model.VoiceStateUpdate;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class JsonEngine implements SerializationEngine {
    
    @Override
    public int serialize(VoiceStateUpdate msg, ByteBuffer buf) {
        int start = buf.position();
        
        buf.put((byte)'{');
        
        writeField(buf, "userId", msg.userId());
        buf.put((byte)',');
        
        writeField(buf, "guildId", msg.guildId());
        buf.put((byte)',');
        
        writeField(buf, "channelId", msg.channelId());
        buf.put((byte)',');
        
        writeField(buf, "muted", msg.muted());
        buf.put((byte)',');
        
        writeField(buf, "deafened", msg.deafened());
        
        buf.put((byte)'}');
        
        return buf.position() - start;
    }
    
    @Override
    public VoiceStateUpdate deserialize(ByteBuffer buf) {
        // Simple JSON parsing (for demo purposes)
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        String json = new String(bytes, StandardCharsets.UTF_8);
        
        long userId = extractLong(json, "userId");
        long guildId = extractLong(json, "guildId");
        long channelId = extractLong(json, "channelId");
        boolean muted = extractBoolean(json, "muted");
        boolean deafened = extractBoolean(json, "deafened");
        
        return new VoiceStateUpdate(userId, guildId, channelId, muted, deafened);
    }
    
    @Override
    public String name() {
        return "JSON";
    }
    
    @Override
    public int estimatedSize() {
        return 180;
    }
    
    private void writeField(ByteBuffer buf, String name, long value) {
        buf.put((byte)'"');
        buf.put(name.getBytes(StandardCharsets.UTF_8));
        buf.put((byte)'"');
        buf.put((byte)':');
        buf.put(Long.toString(value).getBytes(StandardCharsets.UTF_8));
    }
    
    private void writeField(ByteBuffer buf, String name, boolean value) {
        buf.put((byte)'"');
        buf.put(name.getBytes(StandardCharsets.UTF_8));
        buf.put((byte)'"');
        buf.put((byte)':');
        buf.put(value ? "true".getBytes(StandardCharsets.UTF_8) 
                      : "false".getBytes(StandardCharsets.UTF_8));
    }
    
    private long extractLong(String json, String field) {
        String pattern = "\"" + field + "\":";
        int start = json.indexOf(pattern) + pattern.length();
        int end = json.indexOf(',', start);
        if (end == -1) end = json.indexOf('}', start);
        return Long.parseLong(json.substring(start, end).trim());
    }
    
    private boolean extractBoolean(String json, String field) {
        String pattern = "\"" + field + "\":";
        int start = json.indexOf(pattern) + pattern.length();
        return json.substring(start).trim().startsWith("true");
    }
}
