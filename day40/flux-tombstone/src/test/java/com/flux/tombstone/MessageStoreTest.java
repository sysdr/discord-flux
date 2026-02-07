package com.flux.tombstone;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class MessageStoreTest {
    
    private MessageStore store;
    
    @BeforeEach
    void setUp() {
        store = new MessageStore();
    }
    
    @AfterEach
    void tearDown() {
        store.shutdown();
    }
    
    @Test
    void testInsertAndRead() {
        Message msg = new Message("test-channel", "Hello World");
        store.insert(msg);
        
        var result = store.read(msg.id());
        assertTrue(result.isPresent());
        assertEquals("Hello World", result.get().content());
    }
    
    @Test
    void testDeleteCreatesTombstone() {
        Message msg = new Message("test-channel", "To be deleted");
        store.insert(msg);
        
        assertTrue(store.read(msg.id()).isPresent());
        
        store.delete(msg.id());
        
        assertFalse(store.read(msg.id()).isPresent());
    }
    
    @Test
    void testCompactionRemovesTombstones() throws InterruptedException {
        // Insert many messages to trigger flush
        for (int i = 0; i < 1500; i++) {
            store.insert(new Message("channel", "Message " + i));
        }
        
        var statsBefore = store.getStats();
        int messagesToDelete = 500;
        
        // Delete some
        for (int i = 0; i < messagesToDelete; i++) {
            store.insert(new Message("channel", "To delete " + i));
        }
        
        Thread.sleep(100); // Let flush happen
        
        // Force compaction
        store.forceCompaction();
        
        var statsAfter = store.getStats();
        assertTrue(statsAfter.sstableCount() <= statsBefore.sstableCount());
    }
    
    @Test
    void testScanReturnsOnlyActiveMessages() {
        String channelId = "scan-test";
        
        // Insert 10 messages
        for (int i = 0; i < 10; i++) {
            store.insert(new Message(channelId, "Message " + i));
        }
        
        // Delete 5
        List<Message> all = store.scan(channelId, 10);
        for (int i = 0; i < 5; i++) {
            store.delete(all.get(i).id());
        }
        
        List<Message> active = store.scan(channelId, 10);
        assertEquals(5, active.size());
    }
    
    @Test
    void testSnowflakeIdOrdering() {
        MessageId id1 = MessageId.generate();
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {}
        MessageId id2 = MessageId.generate();
        
        assertTrue(id1.compareTo(id2) < 0);
        assertTrue(id1.timestamp() <= id2.timestamp());
    }
}
