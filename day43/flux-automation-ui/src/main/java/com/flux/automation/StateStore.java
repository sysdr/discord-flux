package com.flux.automation;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent append-only log for workflow execution state.
 */
public class StateStore {
    
    private final Path storageDirectory;
    private final Map<String, BufferedWriter> openWriters;
    
    public StateStore(Path storageDirectory) throws IOException {
        this.storageDirectory = storageDirectory;
        this.openWriters = new ConcurrentHashMap<>();
        Files.createDirectories(storageDirectory);
    }
    
    public void appendEvent(String executionId, String eventType, String data) {
        try {
            BufferedWriter writer = getOrCreateWriter(executionId);
            synchronized (writer) {
                String logEntry = String.format("%s|%s|%s|%s%n",
                    Instant.now().toString(), executionId, eventType, data);
                writer.write(logEntry);
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("Failed to write to state store: " + e.getMessage());
        }
    }
    
    public void recordWorkflowStart(WorkflowExecution execution) {
        appendEvent(execution.getExecutionId(), "WORKFLOW_START",
            execution.getWorkflow().name());
    }
    
    public void recordWorkflowComplete(WorkflowExecution execution) {
        String data = String.format("status=%s,duration=%d",
            execution.getStatus(), execution.durationMs());
        appendEvent(execution.getExecutionId(), "WORKFLOW_COMPLETE", data);
        closeWriter(execution.getExecutionId());
    }
    
    public void recordStepResult(String executionId, StepResult result) {
        String data = String.format("step=%s,status=%s,duration=%d,attempt=%d",
            result.stepId(), result.status(), result.durationMs(), result.attemptNumber());
        appendEvent(executionId, "STEP_RESULT", data);
    }
    
    public List<String> replayExecution(String executionId) throws IOException {
        Path logFile = storageDirectory.resolve(executionId + ".log");
        if (!Files.exists(logFile)) {
            return List.of();
        }
        return Files.readAllLines(logFile);
    }
    
    public List<String> listExecutions() throws IOException {
        try (var stream = Files.list(storageDirectory)) {
            return stream
                .filter(path -> path.toString().endsWith(".log"))
                .map(path -> path.getFileName().toString().replace(".log", ""))
                .toList();
        }
    }
    
    private BufferedWriter getOrCreateWriter(String executionId) throws IOException {
        return openWriters.computeIfAbsent(executionId, id -> {
            try {
                Path logFile = storageDirectory.resolve(id + ".log");
                return Files.newBufferedWriter(logFile, 
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
    
    private void closeWriter(String executionId) {
        BufferedWriter writer = openWriters.remove(executionId);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                System.err.println("Failed to close writer: " + e.getMessage());
            }
        }
    }
    
    public void close() {
        for (BufferedWriter writer : openWriters.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        openWriters.clear();
    }
}
