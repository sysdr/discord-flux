package com.flux.readstate;

/**
 * Composite key for a (userId, channelId) read state pair.
 * Records provide equals/hashCode automatically — safe for HashMap use.
 * Production note: At 100M users × 500 channels, consider packing into
 * a single Long if user/channel IDs fit in 32 bits each.
 */
public record AckKey(long userId, long channelId) {}
