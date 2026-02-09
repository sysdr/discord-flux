package com.flux.automation;

import java.time.Instant;
import java.util.Map;

public record StepResult(
    String stepId,
    ExecutionStatus status,
    Map<String, String> outputs,
    String errorMessage,
    Instant startTime,
    Instant endTime,
    int attemptNumber
) {
    public StepResult {
        outputs = outputs != null ? Map.copyOf(outputs) : Map.of();
    }
    
    public static StepResult success(String stepId, Map<String, String> outputs,
                                     Instant startTime, Instant endTime, int attempt) {
        return new StepResult(stepId, ExecutionStatus.COMPLETED, outputs, 
            null, startTime, endTime, attempt);
    }
    
    public static StepResult failure(String stepId, String errorMessage,
                                     Instant startTime, Instant endTime, int attempt) {
        return new StepResult(stepId, ExecutionStatus.FAILED, Map.of(), 
            errorMessage, startTime, endTime, attempt);
    }
    
    public long durationMs() {
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
}
