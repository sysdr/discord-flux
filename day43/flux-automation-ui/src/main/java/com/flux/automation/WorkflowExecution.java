package com.flux.automation;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable state for a single workflow execution.
 * Thread-safe for concurrent step updates.
 */
public class WorkflowExecution {
    private final String executionId;
    private final WorkflowDefinition workflow;
    private final Instant startTime;
    private volatile Instant endTime;
    private volatile ExecutionStatus status;
    private final Map<String, StepResult> stepResults;
    
    public WorkflowExecution(String executionId, WorkflowDefinition workflow) {
        this.executionId = executionId;
        this.workflow = workflow;
        this.startTime = Instant.now();
        this.status = ExecutionStatus.PENDING;
        this.stepResults = new ConcurrentHashMap<>();
    }
    
    public String getExecutionId() { return executionId; }
    public WorkflowDefinition getWorkflow() { return workflow; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public ExecutionStatus getStatus() { return status; }
    
    public synchronized void updateStatus(ExecutionStatus newStatus) {
        this.status = newStatus;
        if (newStatus == ExecutionStatus.COMPLETED || newStatus == ExecutionStatus.FAILED) {
            this.endTime = Instant.now();
        }
    }
    
    public void recordStepResult(StepResult result) {
        stepResults.put(result.stepId(), result);
    }
    
    public StepResult getStepResult(String stepId) {
        return stepResults.get(stepId);
    }
    
    public Map<String, StepResult> getStepResults() {
        return Collections.unmodifiableMap(stepResults);
    }
    
    public long durationMs() {
        if (endTime == null) {
            return Instant.now().toEpochMilli() - startTime.toEpochMilli();
        }
        return endTime.toEpochMilli() - startTime.toEpochMilli();
    }
    
    public double getProgress() {
        int totalSteps = workflow.steps().size();
        int completedSteps = (int) stepResults.values().stream()
            .filter(r -> r.status() == ExecutionStatus.COMPLETED)
            .count();
        return totalSteps == 0 ? 0.0 : (double) completedSteps / totalSteps;
    }
}
