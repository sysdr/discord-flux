package com.flux.automation;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executes individual workflow steps with retry logic and timeout enforcement.
 */
public class StepExecutor {
    
    private final ExecutorService virtualThreadExecutor;
    
    public StepExecutor() {
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public StepResult execute(StepDefinition step, Map<String, String> context) {
        int attempt = 0;
        StepResult lastResult = null;
        
        while (attempt < step.retryAttempts()) {
            attempt++;
            
            try {
                lastResult = executeWithTimeout(step, context, attempt);
                
                if (lastResult.status() == ExecutionStatus.COMPLETED) {
                    return lastResult;
                }
                
                if (attempt < step.retryAttempts()) {
                    long backoffMs = Math.min(1000L * (1L << (attempt - 1)), 60000);
                    Thread.sleep(backoffMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StepResult.failure(step.id(), "Interrupted", 
                    Instant.now(), Instant.now(), attempt);
            } catch (Exception e) {
                lastResult = StepResult.failure(step.id(), e.getMessage(),
                    Instant.now(), Instant.now(), attempt);
            }
        }
        
        return lastResult;
    }
    
    private StepResult executeWithTimeout(StepDefinition step, 
                                          Map<String, String> context,
                                          int attempt) throws Exception {
        CompletableFuture<StepResult> future = CompletableFuture.supplyAsync(
            () -> executeStepLogic(step, context, attempt),
            virtualThreadExecutor
        );
        
        try {
            return future.get(step.timeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return StepResult.failure(step.id(), 
                "Timeout after " + step.timeoutSeconds() + " seconds",
                Instant.now(), Instant.now(), attempt);
        }
    }
    
    private StepResult executeStepLogic(StepDefinition step, 
                                        Map<String, String> context,
                                        int attempt) {
        Instant startTime = Instant.now();
        
        try {
            Map<String, String> outputs = switch (step.type()) {
                case HTTP -> executeHttpStep(step);
                case SCRIPT -> executeScriptStep(step);
                case DATABASE -> executeDatabaseStep(step);
                case EMAIL -> executeEmailStep(step);
                case DELAY -> executeDelayStep(step);
                case CONDITIONAL -> executeConditionalStep(step, context);
                case FAILING -> executeFailingStep(step);
            };
            
            Instant endTime = Instant.now();
            return StepResult.success(step.id(), outputs, startTime, endTime, attempt);
            
        } catch (Exception e) {
            Instant endTime = Instant.now();
            return StepResult.failure(step.id(), e.getMessage(), startTime, endTime, attempt);
        }
    }
    
    private Map<String, String> executeHttpStep(StepDefinition step) throws Exception {
        long durationMs = Long.parseLong(step.config().getOrDefault("durationMs", "1000"));
        Thread.sleep(durationMs);
        return Map.of("statusCode", "200", "responseTime", String.valueOf(durationMs));
    }
    
    private Map<String, String> executeScriptStep(StepDefinition step) throws Exception {
        long durationMs = Long.parseLong(step.config().getOrDefault("durationMs", "1000"));
        Thread.sleep(durationMs);
        return Map.of("exitCode", "0", "output", "Script executed successfully");
    }
    
    private Map<String, String> executeDatabaseStep(StepDefinition step) throws Exception {
        long durationMs = Long.parseLong(step.config().getOrDefault("durationMs", "1000"));
        Thread.sleep(durationMs);
        return Map.of("rowsAffected", "42", "executionTime", String.valueOf(durationMs));
    }
    
    private Map<String, String> executeEmailStep(StepDefinition step) throws Exception {
        long durationMs = Long.parseLong(step.config().getOrDefault("durationMs", "500"));
        Thread.sleep(durationMs);
        return Map.of("messageId", UUID.randomUUID().toString(), "status", "sent");
    }
    
    private Map<String, String> executeDelayStep(StepDefinition step) throws Exception {
        long durationMs = Long.parseLong(step.config().getOrDefault("durationMs", "2000"));
        Thread.sleep(durationMs);
        return Map.of("waited", String.valueOf(durationMs));
    }
    
    private Map<String, String> executeConditionalStep(StepDefinition step, 
                                                       Map<String, String> context) throws Exception {
        long durationMs = Long.parseLong(step.config().getOrDefault("durationMs", "500"));
        Thread.sleep(durationMs);
        return Map.of("condition", "true", "result", "executed");
    }
    
    private Map<String, String> executeFailingStep(StepDefinition step) throws Exception {
        long durationMs = Long.parseLong(step.config().getOrDefault("durationMs", "500"));
        Thread.sleep(durationMs);
        throw new RuntimeException("Step intentionally failed for testing");
    }
    
    public void shutdown() {
        virtualThreadExecutor.shutdown();
    }
}
