package com.flux.presence;

/**
 * Immutable presence update shared across all recipients in a guild.
 * Zero allocation per-send - we serialize once and share the ByteBuffer.
 */
public record PresenceUpdate(
    long userId,
    PresenceStatus status,
    long timestamp,
    String activity  // e.g., "Playing Valorant"
) {
    public PresenceUpdate {
        if (activity != null && activity.length() > 128) {
            activity = activity.substring(0, 128);
        }
    }
    
    /**
     * Wire format size: 8 (userId) + 1 (status) + 8 (timestamp) + 2 (length) + activity bytes
     */
    public int estimateWireSize() {
        int activityBytes = (activity != null) ? activity.length() : 0;
        return 8 + 1 + 8 + 2 + activityBytes;
    }
}
