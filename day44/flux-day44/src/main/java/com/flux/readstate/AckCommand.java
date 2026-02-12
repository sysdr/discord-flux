package com.flux.readstate;

/**
 * Immutable command from the WebSocket handler carrying the client's ack.
 * mentionDelta: how many mentions to clear (negative = clear, 0 = no-op).
 */
public record AckCommand(
    long userId,
    long channelId,
    long messageId,
    int  mentionDelta
) {}
