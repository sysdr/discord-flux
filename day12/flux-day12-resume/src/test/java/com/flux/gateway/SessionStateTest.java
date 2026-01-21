package com.flux.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.channels.SocketChannel;

class SessionStateTest {
    private SessionState session;
    
    @BeforeEach
    void setUp() throws Exception {
        session = new SessionState("test-session", null, 16);
    }
    
    @Test
    void testSequenceIncrement() {
        assertEquals(0, session.nextSequence());
        assertEquals(1, session.nextSequence());
        assertEquals(2, session.nextSequence());
    }
    
    @Test
    void testMessageStorage() {
        Message msg1 = new Message(OpCode.DISPATCH, 0, "{\"test\":1}");
        Message msg2 = new Message(OpCode.DISPATCH, 1, "{\"test\":2}");
        
        session.storeMessage(msg1);
        session.storeMessage(msg2);
        
        Message[] retrieved = session.getMessagesSince(0);
        assertEquals(2, retrieved.length);
    }
    
    @Test
    void testRingBufferWrap() {
        // Fill buffer beyond capacity to test wrapping
        for (int i = 0; i < 20; i++) {
            Message msg = new Message(OpCode.DISPATCH, i, "{\"seq\":" + i + "}");
            session.storeMessage(msg);
        }
        
        // Should only get last 16 messages (buffer size)
        Message[] retrieved = session.getMessagesSince(0);
        assertTrue(retrieved.length <= 16);
    }
    
    @Test
    void testStateTransitions() {
        assertEquals(SessionState.State.ACTIVE, session.getState());
        
        assertTrue(session.disconnect());
        assertEquals(SessionState.State.DISCONNECTED, session.getState());
        
        assertTrue(session.resume(null));
        assertEquals(SessionState.State.ACTIVE, session.getState());
    }
    
    @Test
    void testCannotResumeFromActive() {
        assertEquals(SessionState.State.ACTIVE, session.getState());
        assertFalse(session.resume(null)); // Already active
    }
    
    @Test
    void testExpiredSession() {
        session.disconnect();
        session.expire();
        
        assertEquals(SessionState.State.EXPIRED, session.getState());
        assertFalse(session.resume(null)); // Cannot resume expired
    }
}
