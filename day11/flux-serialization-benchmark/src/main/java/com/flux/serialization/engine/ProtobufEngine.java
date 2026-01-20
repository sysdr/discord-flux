package com.flux.serialization.engine;

import com.flux.serialization.model.VoiceStateUpdate;
import com.flux.serialization.model.MessageProto;
import java.nio.ByteBuffer;

public final class ProtobufEngine implements SerializationEngine {
    
    @Override
    public int serialize(VoiceStateUpdate msg, ByteBuffer buf) {
        MessageProto.VoiceStateUpdate proto = MessageProto.VoiceStateUpdate.newBuilder()
            .setUserId(msg.userId())
            .setGuildId(msg.guildId())
            .setChannelId(msg.channelId())
            .setMuted(msg.muted())
            .setDeafened(msg.deafened())
            .build();
        
        byte[] bytes = proto.toByteArray();
        buf.put(bytes);
        return bytes.length;
    }
    
    @Override
    public VoiceStateUpdate deserialize(ByteBuffer buf) {
        try {
            byte[] bytes = new byte[buf.remaining()];
            buf.get(bytes);
            
            MessageProto.VoiceStateUpdate proto = 
                MessageProto.VoiceStateUpdate.parseFrom(bytes);
            
            return new VoiceStateUpdate(
                proto.getUserId(),
                proto.getGuildId(),
                proto.getChannelId(),
                proto.getMuted(),
                proto.getDeafened()
            );
        } catch (Exception e) {
            throw new RuntimeException("Protobuf deserialization failed", e);
        }
    }
    
    @Override
    public String name() {
        return "Protobuf";
    }
    
    @Override
    public int estimatedSize() {
        return 45;
    }
}
