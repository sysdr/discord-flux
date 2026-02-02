package com.flux.presence;

/**
 * Presence status values matching Discord's model.
 * Ordinal values are used for compact wire format.
 */
public enum PresenceStatus {
    ONLINE(0),
    IDLE(1),
    DND(2),      // Do Not Disturb
    OFFLINE(3);
    
    private final int wireValue;
    
    PresenceStatus(int wireValue) {
        this.wireValue = wireValue;
    }
    
    public int getWireValue() {
        return wireValue;
    }
    
    public static PresenceStatus fromWireValue(int value) {
        return switch (value) {
            case 0 -> ONLINE;
            case 1 -> IDLE;
            case 2 -> DND;
            case 3 -> OFFLINE;
            default -> throw new IllegalArgumentException("Unknown status: " + value);
        };
    }
}
