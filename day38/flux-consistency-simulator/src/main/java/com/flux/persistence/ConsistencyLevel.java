package com.flux.persistence;

public enum ConsistencyLevel {
    ONE(1),
    QUORUM(2), // For RF=3, quorum = 2
    ALL(3);
    
    private final int requiredAcks;
    
    ConsistencyLevel(int requiredAcks) {
        this.requiredAcks = requiredAcks;
    }
    
    public int getRequiredAcks(int replicationFactor) {
        return switch (this) {
            case ONE -> 1;
            case QUORUM -> (replicationFactor / 2) + 1;
            case ALL -> replicationFactor;
        };
    }
}
