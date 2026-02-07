package com.flux.tombstone;

public record Tombstone(MessageId id, long deletedAt) {
    
    public Tombstone(MessageId id) {
        this(id, System.currentTimeMillis());
    }
}
