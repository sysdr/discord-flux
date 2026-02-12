package com.flux.readstate;

/** Lightweight snapshot for dashboard grid rendering — one per entry. */
public record EntrySnapshot(
    long userId,
    long channelId,
    int  state,
    int  mentionCount,
    int  unreadCount
) {}
