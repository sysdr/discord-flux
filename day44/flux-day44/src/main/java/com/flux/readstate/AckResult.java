package com.flux.readstate;

public enum AckResult {
    /** Read pointer actually advanced — entry marked dirty. */
    ADVANCED,
    /** Incoming messageId was <= current lastRead — discarded. */
    STALE,
    /** First ack for this (user, channel) pair — new entry created. */
    CREATED
}
