package com.flux.readstate;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates messages being posted to channels.
 *
 * Posts a random message to a random channel every N ms,
 * updating the channel HEAD pointer in AckTracker.
 * This creates "unread" states for users who haven't acked.
 */
public final class ChannelSimulator {

    public static final int  CHANNEL_COUNT = 20;
    public static final int  USER_COUNT    = 20;

    private final AckTracker            ackTracker;
    private final SnowflakeIdGenerator  snowflake;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> Thread.ofVirtual().name("channel-sim").unstarted(r));

    private final AtomicLong   totalMessagesPosted = new AtomicLong(0);
    private final AtomicBoolean burst              = new AtomicBoolean(false);

    public ChannelSimulator(AckTracker ackTracker, SnowflakeIdGenerator snowflake) {
        this.ackTracker = ackTracker;
        this.snowflake  = snowflake;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::postRandomMessage, 0, 200, TimeUnit.MILLISECONDS);
    }

    private void postRandomMessage() {
        var rng      = ThreadLocalRandom.current();
        int count    = burst.getAndSet(false) ? 50 : 1;
        for (int i = 0; i < count; i++) {
            long channelId = rng.nextLong(1, CHANNEL_COUNT + 1);
            long messageId = snowflake.nextId();
            ackTracker.onNewMessage(channelId, messageId);
            totalMessagesPosted.incrementAndGet();
        }
    }

    /**
     * Seed the tracker with initial read states for users 1..USER_COUNT
     * across all channels. Simulates loading from Cassandra on startup.
     */
    public void seedReadStates() {
        var rng = ThreadLocalRandom.current();
        for (int userId = 1; userId <= USER_COUNT; userId++) {
            for (int channelId = 1; channelId <= CHANNEL_COUNT; channelId++) {
                long msgId = snowflake.nextId();
                ackTracker.onNewMessage(channelId, msgId);
                // Most users are "read" — only some channels are unread
                if (rng.nextDouble() > 0.3) {
                    ackTracker.ack(new AckCommand(userId, channelId, msgId, 0));
                }
            }
        }
    }

    public void triggerBurst()    { burst.set(true); }
    public void stop()            { scheduler.shutdownNow(); }
    public long getTotalMessages(){ return totalMessagesPosted.get(); }
}
