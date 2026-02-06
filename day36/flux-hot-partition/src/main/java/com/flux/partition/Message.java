package com.flux.partition;

/**
 * Immutable message record representing a chat message.
 */
public record Message(
        long messageId,
        long channelId,
        long authorId,
        String content,
        long timestamp
) {
    public Message {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
    }

    /**
     * Estimate the size of this message in bytes (for partition size calculations).
     */
    public long estimatedBytes() {
        return 8 + 8 + 8 + content.length() + 8; // IDs + content + timestamp
    }
}
