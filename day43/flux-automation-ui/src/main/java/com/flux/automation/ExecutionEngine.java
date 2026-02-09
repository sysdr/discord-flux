package com.flux.automation;

import java.util.*;
import java.util.concurrent.*;

/**
 * Core workflow execution engine with parallel step execution.
 */
public class ExecutionEngine {
    
    private final StepExecutor stepExecutor;
    private final TopologicalExecutionPlanner planner;
    private final Map<String, WorkflowExecution> activeExecutions;
    private final ExecutorService virtualThreadExecutor;
    
    public ExecutionEngine() {
        this.stepExecutor = new StepExecutor();
        this.planner = new TopologicalExecutionPlanner();
        this.activeExecutions = new ConcurrentHashMap<>();
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    /**
     * Execute workflow asynchronously. Returns both the execution (immediately) and
     * a future that completes when the workflow finishes.
     */
    public record ExecuteResult(WorkflowExecution execution, CompletableFuture<WorkflowExecution> future) {}
    
    public ExecuteResult executeAsync(WorkflowDefinition workflow) {
        String executionId = UUID.randomUUID().toString();
        WorkflowExecution execution = new WorkflowExecution(executionId, workflow);
        activeExecutions.put(executionId, execution);
        
        CompletableFuture<WorkflowExecution> future = CompletableFuture.supplyAsync(
            () -> executeWorkflow(execution),
            virtualThreadExecutor
        );
        return new ExecuteResult(execution, future);
    }
    
    private WorkflowExecution executeWorkflow(WorkflowExecution execution) {
        execution.updateStatus(ExecutionStatus.RUNNING);
        
        try {
            List<Set<StepDefinition>> levels = planner.computeExecutionLevels(
                execution.getWorkflow());
            
            for (Set<StepDefinition> level : levels) {
                boolean levelSuccess = executeLevel(level, execution);
                
                if (!levelSuccess) {
                    execution.updateStatus(ExecutionStatus.FAILED);
                    return execution;
                }
            }
            
            execution.updateStatus(ExecutionStatus.COMPLETED);
            
        } catch (Exception e) {
            execution.updateStatus(ExecutionStatus.FAILED);
        } finally {
            activeExecutions.remove(execution.getExecutionId());
        }
        
        return execution;
    }
    
    private boolean executeLevel(Set<StepDefinition> level, WorkflowExecution execution) {
        Map<String, String> context = buildContext(execution);
        
        List<CompletableFuture<StepResult>> futures = level.stream()
            .map(step -> CompletableFuture.supplyAsync(
                () -> stepExecutor.execute(step, context),
                virtualThreadExecutor
            ))
            .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        boolean allSuccess = true;
        for (CompletableFuture<StepResult> future : futures) {
            StepResult result = future.join();
            execution.recordStepResult(result);
            
            if (result.status() != ExecutionStatus.COMPLETED) {
                allSuccess = false;
            }
        }
        
        return allSuccess;
    }
    
    private Map<String, String> buildContext(WorkflowExecution execution) {
        Map<String, String> context = new HashMap<>();
        for (var entry : execution.getStepResults().entrySet()) {
            String stepId = entry.getKey();
            StepResult result = entry.getValue();
            for (var output : result.outputs().entrySet()) {
                context.put(stepId + "." + output.getKey(), output.getValue());
            }
        }
        return context;
    }
    
    public WorkflowExecution getExecution(String executionId) {
        return activeExecutions.get(executionId);
    }
    
    public Collection<WorkflowExecution> getActiveExecutions() {
        return Collections.unmodifiableCollection(activeExecutions.values());
    }
    
    public void shutdown() {
        stepExecutor.shutdown();
        virtualThreadExecutor.shutdown();
    }
}
