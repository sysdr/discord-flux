package com.flux.storage;

import com.flux.model.Message;
import com.flux.model.SnowflakeId;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public record SSTable(
    Path file,
    SnowflakeId minKey,
    SnowflakeId maxKey,
    long createdAt,
    long sizeBytes
) {
    public static SSTable write(Path outputDir, SortedMap<SnowflakeId, Message> data) throws IOException {
        long timestamp = System.currentTimeMillis();
        Path file = outputDir.resolve("sstable-" + timestamp + ".db");
        
        long bytesWritten = 0;
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(file)))) {
            
            out.writeInt(data.size());
            bytesWritten += 4;
            
            for (var entry : data.entrySet()) {
                out.writeLong(entry.getKey().value());
                bytesWritten += 8;
                
                Message msg = entry.getValue();
                out.writeLong(msg.channelId());
                out.writeLong(msg.authorId());
                out.writeLong(msg.createdAt());
                bytesWritten += 24;
                
                byte[] content = msg.content().getBytes(StandardCharsets.UTF_8);
                out.writeInt(content.length);
                out.write(content);
                bytesWritten += 4 + content.length;
            }
        }
        
        return new SSTable(
            file,
            data.firstKey(),
            data.lastKey(),
            timestamp,
            bytesWritten
        );
    }
    
    public List<Message> read() throws IOException {
        List<Message> messages = new ArrayList<>();
        
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                long idValue = in.readLong();
                SnowflakeId id = new SnowflakeId(idValue);
                
                long channelId = in.readLong();
                long authorId = in.readLong();
                long createdAt = in.readLong();
                
                int contentLen = in.readInt();
                byte[] contentBytes = new byte[contentLen];
                in.readFully(contentBytes);
                String content = new String(contentBytes, StandardCharsets.UTF_8);
                
                messages.add(new Message(id, channelId, authorId, content, createdAt));
            }
        }
        
        return messages;
    }
    
    public Message get(SnowflakeId key) throws IOException {
        if (key.compareTo(minKey) < 0 || key.compareTo(maxKey) > 0) {
            return null;
        }
        
        // Linear scan (in production, you'd use bloom filters + sparse index)
        for (Message msg : read()) {
            if (msg.id().equals(key)) {
                return msg;
            }
        }
        return null;
    }
    
    public long timeWindowBucket(long windowSizeMs) {
        return createdAt / windowSizeMs;
    }
    
    public void delete() throws IOException {
        Files.deleteIfExists(file);
    }
}
