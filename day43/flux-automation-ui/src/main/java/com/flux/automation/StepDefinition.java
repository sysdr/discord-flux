package com.flux.automation;

import java.util.*;

/**
 * Immutable definition of a single workflow step.
 * Uses Record for zero-allocation, type-safe data.
 */
public record StepDefinition(
    String id,
    String name,
    StepType type,
    Map<String, String> config,
    List<String> dependsOn,
    int retryAttempts,
    int timeoutSeconds
) {
    public StepDefinition {
        config = Map.copyOf(config);
        dependsOn = List.copyOf(dependsOn);
    }
    
    public static Builder builder(String id, String name, StepType type) {
        return new Builder(id, name, type);
    }
    
    public static class Builder {
        private final String id;
        private final String name;
        private final StepType type;
        private Map<String, String> config = new HashMap<>();
        private List<String> dependsOn = new ArrayList<>();
        private int retryAttempts = 3;
        private int timeoutSeconds = 30;
        
        Builder(String id, String name, StepType type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }
        
        public Builder config(String key, String value) {
            this.config.put(key, value);
            return this;
        }
        
        public Builder dependsOn(String... stepIds) {
            this.dependsOn.addAll(Arrays.asList(stepIds));
            return this;
        }
        
        public Builder retryAttempts(int attempts) {
            this.retryAttempts = attempts;
            return this;
        }
        
        public Builder timeoutSeconds(int seconds) {
            this.timeoutSeconds = seconds;
            return this;
        }
        
        public StepDefinition build() {
            return new StepDefinition(id, name, type, config, dependsOn, 
                retryAttempts, timeoutSeconds);
        }
    }
}
