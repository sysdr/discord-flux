package com.flux.presence.core;

public enum PresenceStatus {
    ONLINE("online"),
    IDLE("idle"),
    OFFLINE("offline"),
    UNKNOWN("unknown");
    
    private final String value;
    
    PresenceStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static PresenceStatus fromString(String value) {
        if (value == null) return OFFLINE;
        return switch (value.toLowerCase()) {
            case "online" -> ONLINE;
            case "idle" -> IDLE;
            case "offline" -> OFFLINE;
            default -> UNKNOWN;
        };
    }
}
