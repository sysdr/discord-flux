package com.flux.migration.test;

import com.flux.migration.Message;
import com.flux.migration.StreamingJsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StreamingParserTest {
    
    @Test
    void testParsesSingleMessage(@TempDir Path tempDir) throws IOException {
        Path jsonFile = tempDir.resolve("test.json");
        Files.writeString(jsonFile, """
            [
                {
                    "id": 1,
                    "channelId": 100,
                    "userId": "user123",
                    "content": "Hello World",
                    "timestamp": 1640000000000
                }
            ]
            """);
        
        try (StreamingJsonParser parser = new StreamingJsonParser()) {
            List<Message> messages = parser.stream(jsonFile).toList();
            
            assertEquals(1, messages.size());
            assertEquals(1L, messages.get(0).id());
            assertEquals("Hello World", messages.get(0).content());
        }
    }
    
    @Test
    void testParsesLargeFile(@TempDir Path tempDir) throws IOException {
        // Generate 10,000 messages
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 10_000; i++) {
            if (i > 0) json.append(",");
            json.append(String.format("""
                {
                    "id": %d,
                    "channelId": 100,
                    "userId": "user%d",
                    "content": "Message %d",
                    "timestamp": %d
                }
                """, i, i, i, System.currentTimeMillis()));
        }
        json.append("]");
        
        Path jsonFile = tempDir.resolve("large.json");
        Files.writeString(jsonFile, json.toString());
        
        try (StreamingJsonParser parser = new StreamingJsonParser()) {
            long count = parser.stream(jsonFile).count();
            assertEquals(10_000, count);
        }
    }
}
