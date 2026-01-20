package com.flux.session;

public enum SessionState {
    CONNECTING,  // Initial handshake in progress
    ACTIVE,      // Fully connected, processing messages
    IDLE,        // No activity for some time
    ZOMBIE       // Connection closed but not cleaned up yet
}
