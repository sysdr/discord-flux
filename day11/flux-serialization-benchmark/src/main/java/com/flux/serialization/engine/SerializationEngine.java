package com.flux.serialization.engine;

import com.flux.serialization.model.VoiceStateUpdate;
import java.nio.ByteBuffer;

public sealed interface SerializationEngine 
    permits JsonEngine, ProtobufEngine, CustomBinaryEngine {
    
    /**
     * Serialize message into buffer. Returns bytes written.
     */
    int serialize(VoiceStateUpdate msg, ByteBuffer buf);
    
    /**
     * Deserialize message from buffer.
     */
    VoiceStateUpdate deserialize(ByteBuffer buf);
    
    /**
     * Engine name for metrics.
     */
    String name();
    
    /**
     * Estimated size for this message type.
     */
    int estimatedSize();
}
