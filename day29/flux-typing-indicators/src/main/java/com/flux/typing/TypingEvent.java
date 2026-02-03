package com.flux.typing;

public record TypingEvent(
    long timestamp,
    long userId,
    long channelId
) {
    public boolean isExpired(long currentNanos, long ttlNanos) {
        return (currentNanos - timestamp) > ttlNanos;
    }
}
