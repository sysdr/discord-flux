package com.flux.readstate;

/**
 * Point-in-time snapshot of a single read state entry.
 * Returned from read APIs — never mutated after creation.
 */
public record ReadSnapshot(
    long   userId,
    long   channelId,
    long   lastReadMessageId,
    long   channelLatestMessageId,
    int    unreadCount,
    int    mentionCount,
    int    state,           // 0=CLEAN, 1=DIRTY, 2=FLUSHING, -1=NOT_FOUND
    String stateLabel
) {
    public static String labelFor(int state) {
        return switch (state) {
            case 0  -> "CLEAN";
            case 1  -> "DIRTY";
            case 2  -> "FLUSHING";
            case -1 -> "COLD";
            default -> "UNKNOWN";
        };
    }
}
