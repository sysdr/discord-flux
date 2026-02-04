package com.flux.model;

public record ChannelStats(
    long channelId,
    long messageCount,
    long totalBytes,
    double avgMessageSize
) {
    public static ChannelStats empty(long channelId) {
        return new ChannelStats(channelId, 0, 0, 0.0);
    }
}
