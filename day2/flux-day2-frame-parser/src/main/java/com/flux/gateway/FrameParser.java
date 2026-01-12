package com.flux.gateway;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stateful WebSocket frame parser.
 * Handles partial reads and variable-length headers without allocations.
 */
public class FrameParser {
    
    public enum State {
        READING_HEADER,
        READING_EXTENDED_LENGTH_16,
        READING_EXTENDED_LENGTH_64,
        READING_MASK,
        READING_PAYLOAD,
        COMPLETE,
        ERROR
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.READING_HEADER);
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(14); // Max header size
    private ByteBuffer payloadBuffer;

    // Frame components
    private boolean fin;
    private int opcode;
    private boolean masked;
    private long payloadLength;
    private byte[] maskKey = new byte[4];
    private int bytesRead;

    public FrameParser() {
        reset();
    }

    /**
     * Feed bytes into the parser. Returns frame when complete, null otherwise.
     * Critical: This method MUST NOT allocate on the hot path.
     */
    public WebSocketFrame parse(ByteBuffer input) {
        while (input.hasRemaining()) {
            State currentState = state.get();
            
            switch (currentState) {
                case READING_HEADER -> {
                    if (!readHeader(input)) {
                        return null; // Need more bytes
                    }
                }
                case READING_EXTENDED_LENGTH_16 -> {
                    if (!readExtendedLength16(input)) {
                        return null;
                    }
                }
                case READING_EXTENDED_LENGTH_64 -> {
                    if (!readExtendedLength64(input)) {
                        return null;
                    }
                }
                case READING_MASK -> {
                    if (!readMask(input)) {
                        return null;
                    }
                }
                case READING_PAYLOAD -> {
                    if (!readPayload(input)) {
                        return null;
                    }
                }
                case COMPLETE -> {
                    return buildFrame();
                }
                case ERROR -> {
                    throw new IllegalStateException("Parser in error state");
                }
            }
            
            // Check if frame is complete after any read operation
            if (state.get() == State.COMPLETE) {
                return buildFrame();
            }
        }
        
        // Check state after loop in case frame completed but no more input
        if (state.get() == State.COMPLETE) {
            return buildFrame();
        }
        
        return null;
    }

    private boolean readHeader(ByteBuffer input) {
        // Read minimum 2 bytes for basic header
        while (headerBuffer.position() < 2 && input.hasRemaining()) {
            headerBuffer.put(input.get());
        }

        if (headerBuffer.position() < 2) {
            return false; // Need more data
        }

        // Parse first two bytes
        headerBuffer.flip();
        byte b0 = headerBuffer.get();
        byte b1 = headerBuffer.get();
        headerBuffer.compact();

        fin = (b0 & 0x80) != 0;
        opcode = b0 & 0x0F;
        masked = (b1 & 0x80) != 0;
        int payloadLenIndicator = b1 & 0x7F;

        if (payloadLenIndicator < 126) {
            payloadLength = payloadLenIndicator;
            if (payloadLength == 0 && !masked) {
                // Zero-length unmasked frame - complete immediately
                payloadBuffer = ByteBuffer.allocate(0);
                payloadBuffer.flip();
                state.set(State.COMPLETE);
            } else {
                state.set(masked ? State.READING_MASK : State.READING_PAYLOAD);
                allocatePayloadBuffer();
            }
        } else if (payloadLenIndicator == 126) {
            state.set(State.READING_EXTENDED_LENGTH_16);
        } else {
            state.set(State.READING_EXTENDED_LENGTH_64);
        }

        return true;
    }

    private boolean readExtendedLength16(ByteBuffer input) {
        while (headerBuffer.position() < 2 && input.hasRemaining()) {
            headerBuffer.put(input.get());
        }

        if (headerBuffer.position() < 2) {
            return false;
        }

        headerBuffer.flip();
        payloadLength = Short.toUnsignedInt(headerBuffer.getShort());
        headerBuffer.clear();
        state.set(masked ? State.READING_MASK : State.READING_PAYLOAD);
        allocatePayloadBuffer();
        return true;
    }

    private boolean readExtendedLength64(ByteBuffer input) {
        while (headerBuffer.position() < 8 && input.hasRemaining()) {
            headerBuffer.put(input.get());
        }

        if (headerBuffer.position() < 8) {
            return false;
        }

        headerBuffer.flip();
        payloadLength = headerBuffer.getLong();
        headerBuffer.clear();
        
        // Sanity check: reject frames > 10MB
        if (payloadLength > 10 * 1024 * 1024) {
            state.set(State.ERROR);
            throw new IllegalStateException("Payload too large: " + payloadLength);
        }

        state.set(masked ? State.READING_MASK : State.READING_PAYLOAD);
        allocatePayloadBuffer();
        return true;
    }

    private boolean readMask(ByteBuffer input) {
        while (bytesRead < 4 && input.hasRemaining()) {
            maskKey[bytesRead++] = input.get();
        }

        if (bytesRead < 4) {
            return false;
        }

        bytesRead = 0;
        state.set(State.READING_PAYLOAD);
        return true;
    }

    private boolean readPayload(ByteBuffer input) {
        if (payloadLength == 0) {
            // For zero-length frames, create empty buffer
            payloadBuffer = ByteBuffer.allocate(0);
            payloadBuffer.flip();
            state.set(State.COMPLETE);
            return true;
        }

        // Transfer available bytes
        int toRead = (int) Math.min(payloadLength - bytesRead, input.remaining());
        int oldLimit = input.limit();
        input.limit(input.position() + toRead);
        payloadBuffer.put(input);
        input.limit(oldLimit);
        bytesRead += toRead;

        if (bytesRead >= payloadLength) {
            if (masked) {
                unmaskPayload();
            } else {
                // Flip buffer for reading if not masked
                payloadBuffer.flip();
            }
            state.set(State.COMPLETE);
            return true;
        }

        return false;
    }

    private void allocatePayloadBuffer() {
        if (payloadLength > 0) {
            payloadBuffer = ByteBuffer.allocate((int) payloadLength);
        }
    }

    private void unmaskPayload() {
        payloadBuffer.flip();
        for (int i = 0; i < payloadLength; i++) {
            byte b = payloadBuffer.get(i);
            payloadBuffer.put(i, (byte) (b ^ maskKey[i % 4]));
        }
    }

    private WebSocketFrame buildFrame() {
        ByteBuffer payload = payloadBuffer != null ? payloadBuffer.asReadOnlyBuffer() : ByteBuffer.allocate(0);
        WebSocketFrame frame = new WebSocketFrame(fin, opcode, masked, payload);
        reset();
        return frame;
    }

    public void reset() {
        state.set(State.READING_HEADER);
        headerBuffer.clear();
        payloadBuffer = null;
        bytesRead = 0;
        payloadLength = 0;
    }

    public State getState() {
        return state.get();
    }
}
