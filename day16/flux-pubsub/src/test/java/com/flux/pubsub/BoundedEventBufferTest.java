package com.flux.pubsub;

import com.flux.pubsub.core.BoundedEventBuffer;
import com.flux.pubsub.core.EventType;
import com.flux.pubsub.core.GuildEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundedEventBufferTest {

    @Test
    void testOfferAndPoll() {
        BoundedEventBuffer buffer = new BoundedEventBuffer(5);
        GuildEvent event = new GuildEvent(1, EventType.MESSAGE_CREATE, System.currentTimeMillis(), "test");

        assertTrue(buffer.offer(event));
        assertEquals(1, buffer.size());

        GuildEvent polled = buffer.poll();
        assertNotNull(polled);
        assertEquals(event.payload(), polled.payload());
        assertEquals(0, buffer.size());
    }

    @Test
    void testDropsOldestWhenFull() {
        BoundedEventBuffer buffer = new BoundedEventBuffer(3);

        for (int i = 0; i < 5; i++) {
            buffer.offer(new GuildEvent(1, EventType.MESSAGE_CREATE, i, "msg-" + i));
        }

        assertEquals(3, buffer.size());
        assertEquals(2, buffer.getDroppedCount()); // Dropped 2 oldest

        GuildEvent first = buffer.poll();
        assertEquals("msg-2", first.payload()); // Oldest available is msg-2
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        BoundedEventBuffer buffer = new BoundedEventBuffer(1000);

        Thread producer = Thread.startVirtualThread(() -> {
            for (int i = 0; i < 1000; i++) {
                buffer.offer(new GuildEvent(1, EventType.MESSAGE_CREATE, i, "msg-" + i));
            }
        });

        Thread consumer = Thread.startVirtualThread(() -> {
            int consumed = 0;
            while (consumed < 1000) {
                if (buffer.poll() != null) {
                    consumed++;
                }
            }
        });

        producer.join();
        consumer.join();

        assertEquals(0, buffer.size());
    }
}
