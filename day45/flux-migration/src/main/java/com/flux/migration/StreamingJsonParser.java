package com.flux.migration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Zero-copy streaming JSON parser that never materializes the entire dataset in heap.
 * Uses memory-mapped files for OS-level page cache efficiency.
 */
public class StreamingJsonParser implements AutoCloseable {
    
    private final JsonFactory jsonFactory = new JsonFactory();
    private final AtomicLong parsedCount = new AtomicLong(0);
    private FileChannel channel;
    
    public Stream<Message> stream(Path jsonFile) throws IOException {
        channel = FileChannel.open(jsonFile, StandardOpenOption.READ);
        long fileSize = channel.size();
        
        // Memory-mapped buffer for zero-copy reads (off-heap)
        ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize);
        
        InputStream inputStream = new ByteBufferBackedInputStream(buffer);
        JsonParser parser = jsonFactory.createParser(inputStream);
        
        // Advance to start of array
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw new IllegalStateException("Expected JSON array");
        }
        
        return StreamSupport.stream(
            new MessageSpliterator(parser, parsedCount),
            false // Not parallel - we control parallelism at write level
        ).onClose(this::close);
    }
    
    public long getParsedCount() {
        return parsedCount.get();
    }
    
    @Override
    public void close() {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    /**
     * Custom Spliterator that parses one message at a time
     */
    private static class MessageSpliterator implements Spliterator<Message> {
        private final JsonParser parser;
        private final AtomicLong counter;
        
        MessageSpliterator(JsonParser parser, AtomicLong counter) {
            this.parser = parser;
            this.counter = counter;
        }
        
        @Override
        public boolean tryAdvance(Consumer<? super Message> action) {
            try {
                JsonToken token = parser.nextToken();
                
                if (token == JsonToken.END_ARRAY || token == null) {
                    return false;
                }
                
                if (token == JsonToken.START_OBJECT) {
                    Message msg = parseMessage(parser);
                    counter.incrementAndGet();
                    action.accept(msg);
                    return true;
                }
                
                return tryAdvance(action); // Skip unexpected tokens
                
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        
        private Message parseMessage(JsonParser parser) throws IOException {
            long id = 0, channelId = 0;
            String userId = "", content = "";
            Instant timestamp = Instant.now();
            
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                
                switch (fieldName) {
                    case "id" -> id = parser.getLongValue();
                    case "channelId" -> channelId = parser.getLongValue();
                    case "userId" -> userId = parser.getText();
                    case "content" -> content = parser.getText();
                    case "timestamp" -> timestamp = Instant.ofEpochMilli(parser.getLongValue());
                }
            }
            
            return new Message(id, channelId, userId, content, timestamp);
        }
        
        @Override
        public Spliterator<Message> trySplit() {
            return null; // No splitting - we control parallelism externally
        }
        
        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }
        
        @Override
        public int characteristics() {
            return ORDERED | NONNULL | IMMUTABLE;
        }
    }
    
    /**
     * InputStream adapter for ByteBuffer (avoids array copy)
     */
    private static class ByteBufferBackedInputStream extends InputStream {
        private final ByteBuffer buffer;
        
        ByteBufferBackedInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }
        
        @Override
        public int read() {
            return buffer.hasRemaining() ? buffer.get() & 0xFF : -1;
        }
        
        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (!buffer.hasRemaining()) return -1;
            
            int toRead = Math.min(length, buffer.remaining());
            buffer.get(bytes, offset, toRead);
            return toRead;
        }
    }
}
