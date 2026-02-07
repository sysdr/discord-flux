package com.flux.tombstone;

public record Message(MessageId id, String channelId, String content, long createdAt) {
    
    public Message(String channelId, String content) {
        this(MessageId.generate(), channelId, content, System.currentTimeMillis());
    }
}
