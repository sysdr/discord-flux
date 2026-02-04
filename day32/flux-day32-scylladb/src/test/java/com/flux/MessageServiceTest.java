package com.flux;

import com.flux.model.Message;
import com.flux.service.MessageService;
import com.flux.service.ScyllaConnection;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Requires ScyllaDB running - enable for integration tests")
class MessageServiceTest {
    private static ScyllaConnection connection;
    private static MessageService messageService;

    @BeforeAll
    static void setup() {
        connection = new ScyllaConnection("127.0.0.1", 9042, "datacenter1");
        connection.initializeSchema();
        messageService = new MessageService(connection);
    }

    @AfterAll
    static void teardown() {
        if (connection != null) connection.close();
    }

    @Test
    void insertAndRetrieveMessage() {
        Message msg = Message.create(999L, 1L, "Test content");
        messageService.insertMessage(msg);
        List<Message> messages = messageService.getLatestMessages(999L, 10);
        assertFalse(messages.isEmpty());
        assertEquals(999L, messages.get(0).channelId());
        assertEquals("Test content", messages.get(0).content());
    }

    @Test
    void getChannelStats() {
        var stats = messageService.getChannelStats(999L);
        assertNotNull(stats);
        assertTrue(stats.messageCount() >= 0);
    }
}
