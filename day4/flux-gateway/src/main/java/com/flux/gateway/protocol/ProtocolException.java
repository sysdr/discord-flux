package com.flux.gateway.protocol;

/**
 * Thrown when protocol parsing fails.
 */
public class ProtocolException extends RuntimeException {
    public ProtocolException(String message) {
        super(message);
    }
}
