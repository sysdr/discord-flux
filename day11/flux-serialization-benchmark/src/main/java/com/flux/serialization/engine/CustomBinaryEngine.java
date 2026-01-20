package com.flux.serialization.engine;

import com.flux.serialization.model.VoiceStateUpdate;
import java.nio.ByteBuffer;

public final class CustomBinaryEngine implements SerializationEngine {
    
    private static final byte VERSION = 0x01;
    private static final byte TYPE_LONG = 0x10;
    private static final byte TYPE_BOOL = 0x02;
    
    @Override
    public int serialize(VoiceStateUpdate msg, ByteBuffer buf) {
        int start = buf.position();
        
        // Header
        buf.put(VERSION);
        buf.put((byte)5); // field count
        
        // userId
        buf.put(TYPE_LONG);
        buf.putLong(msg.userId());
        
        // guildId
        buf.put(TYPE_LONG);
        buf.putLong(msg.guildId());
        
        // channelId
        buf.put(TYPE_LONG);
        buf.putLong(msg.channelId());
        
        // muted
        buf.put(TYPE_BOOL);
        buf.put((byte)(msg.muted() ? 1 : 0));
        
        // deafened
        buf.put(TYPE_BOOL);
        buf.put((byte)(msg.deafened() ? 1 : 0));
        
        return buf.position() - start;
    }
    
    @Override
    public VoiceStateUpdate deserialize(ByteBuffer buf) {
        byte version = buf.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unknown version: " + version);
        }
        
        byte fieldCount = buf.get();
        if (fieldCount != 5) {
            throw new IllegalArgumentException("Expected 5 fields, got: " + fieldCount);
        }
        
        // Read userId
        if (buf.get() != TYPE_LONG) throw new IllegalStateException("Expected LONG type");
        long userId = buf.getLong();
        
        // Read guildId
        if (buf.get() != TYPE_LONG) throw new IllegalStateException("Expected LONG type");
        long guildId = buf.getLong();
        
        // Read channelId
        if (buf.get() != TYPE_LONG) throw new IllegalStateException("Expected LONG type");
        long channelId = buf.getLong();
        
        // Read muted
        if (buf.get() != TYPE_BOOL) throw new IllegalStateException("Expected BOOL type");
        boolean muted = buf.get() == 1;
        
        // Read deafened
        if (buf.get() != TYPE_BOOL) throw new IllegalStateException("Expected BOOL type");
        boolean deafened = buf.get() == 1;
        
        return new VoiceStateUpdate(userId, guildId, channelId, muted, deafened);
    }
    
    @Override
    public String name() {
        return "CustomBinary";
    }
    
    @Override
    public int estimatedSize() {
        return 33; // 2 header + 3*(1+8) + 2*(1+1)
    }
}
