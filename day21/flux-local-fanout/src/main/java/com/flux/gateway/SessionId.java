package com.flux.gateway;

import java.util.UUID;

public record SessionId(String value) {
    public static SessionId generate() {
        return new SessionId(UUID.randomUUID().toString());
    }
    
    @Override
    public String toString() {
        return value;
    }
}
