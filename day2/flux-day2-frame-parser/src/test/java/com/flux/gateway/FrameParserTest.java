package com.flux.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebSocket frame parser.
 */
class FrameParserTest {

    private FrameParser parser;

    @BeforeEach
    void setUp() {
        parser = new FrameParser();
    }

    @Test
    void testSimpleTextFrame() {
        // FIN=1, OPCODE=TEXT, MASKED=1, Length=5, Mask=0x12345678
        ByteBuffer buffer = ByteBuffer.allocate(11);
        buffer.put((byte) 0x81); // FIN=1, OPCODE=TEXT
        buffer.put((byte) 0x85); // MASKED=1, Length=5
        buffer.put(new byte[]{0x12, 0x34, 0x56, 0x78}); // Mask
        buffer.put((byte) ('H' ^ 0x12)); // Masked "Hello"
        buffer.put((byte) ('e' ^ 0x34));
        buffer.put((byte) ('l' ^ 0x56));
        buffer.put((byte) ('l' ^ 0x78));
        buffer.put((byte) ('o' ^ 0x12));
        buffer.flip();

        WebSocketFrame frame = parser.parse(buffer);
        assertNotNull(frame);
        assertTrue(frame.fin());
        assertEquals(WebSocketFrame.OPCODE_TEXT, frame.opcode());
        assertTrue(frame.masked());
        assertEquals("Hello", frame.getPayloadAsText());
    }

    @Test
    void testPingFrame() {
        // FIN=1, OPCODE=PING, MASKED=0, Length=0
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.put((byte) 0x89); // FIN=1, OPCODE=PING
        buffer.put((byte) 0x00); // MASKED=0, Length=0
        buffer.flip();

        WebSocketFrame frame = parser.parse(buffer);
        assertNotNull(frame);
        assertTrue(frame.fin());
        assertEquals(WebSocketFrame.OPCODE_PING, frame.opcode());
        assertFalse(frame.masked());
    }

    @Test
    void testExtendedLength16() {
        // FIN=1, OPCODE=BINARY, MASKED=0, Length=126 (next 2 bytes = 200)
        ByteBuffer buffer = ByteBuffer.allocate(204);
        buffer.put((byte) 0x82); // FIN=1, OPCODE=BINARY
        buffer.put((byte) 0x7E); // MASKED=0, Length=126 (extended)
        buffer.putShort((short) 200); // Actual length
        for (int i = 0; i < 200; i++) {
            buffer.put((byte) i);
        }
        buffer.flip();

        WebSocketFrame frame = parser.parse(buffer);
        assertNotNull(frame);
        assertEquals(WebSocketFrame.OPCODE_BINARY, frame.opcode());
        assertEquals(200, frame.payload().remaining());
    }

    @Test
    void testPartialFrame() {
        // Send only first byte
        ByteBuffer buffer1 = ByteBuffer.allocate(1);
        buffer1.put((byte) 0x81);
        buffer1.flip();

        WebSocketFrame frame1 = parser.parse(buffer1);
        assertNull(frame1); // Incomplete

        // Send rest of header + payload
        ByteBuffer buffer2 = ByteBuffer.allocate(10);
        buffer2.put((byte) 0x85); // MASKED=1, Length=5
        buffer2.put(new byte[]{0x00, 0x00, 0x00, 0x00}); // Mask (zeros for simplicity)
        buffer2.put("World".getBytes());
        buffer2.flip();

        WebSocketFrame frame2 = parser.parse(buffer2);
        assertNotNull(frame2);
        assertEquals("World", frame2.getPayloadAsText());
    }

    @Test
    void testCloseFrame() {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.put((byte) 0x88); // FIN=1, OPCODE=CLOSE
        buffer.put((byte) 0x00);
        buffer.flip();

        WebSocketFrame frame = parser.parse(buffer);
        assertNotNull(frame);
        assertEquals(WebSocketFrame.OPCODE_CLOSE, frame.opcode());
        assertTrue(frame.isControlFrame());
    }
}
