package com.flux.automation;

import java.util.*;

/**
 * Immutable workflow definition (DAG of steps).
 * Thread-safe, can be shared across executions.
 */
public record WorkflowDefinition(
    String id,
    String name,
    String description,
    List<StepDefinition> steps
) {
    public WorkflowDefinition {
        steps = steps != null ? List.copyOf(steps) : List.of();
        validate(steps);
    }
    
    /**
     * Validate workflow: no cycles, all dependencies exist.
     */
    private void validate(List<StepDefinition> stepsToValidate) {
        Set<String> stepIds = new HashSet<>();
        for (StepDefinition step : stepsToValidate) {
            stepIds.add(step.id());
        }
        
        for (StepDefinition step : stepsToValidate) {
            for (String dep : step.dependsOn()) {
                if (!stepIds.contains(dep)) {
                    throw new IllegalArgumentException(
                        "Step " + step.id() + " depends on non-existent step: " + dep
                    );
                }
            }
        }
        
        if (hasCycle(stepsToValidate)) {
            throw new IllegalArgumentException("Workflow contains cycles");
        }
    }
    
    private boolean hasCycle(List<StepDefinition> stepsToValidate) {
        Map<String, Set<String>> graph = buildDependencyGraph(stepsToValidate);
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String stepId : graph.keySet()) {
            if (hasCycleDFS(stepId, graph, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean hasCycleDFS(String node, Map<String, Set<String>> graph,
                                Set<String> visited, Set<String> recursionStack) {
        visited.add(node);
        recursionStack.add(node);
        
        for (String neighbor : graph.getOrDefault(node, Set.of())) {
            if (!visited.contains(neighbor)) {
                if (hasCycleDFS(neighbor, graph, visited, recursionStack)) {
                    return true;
                }
            } else if (recursionStack.contains(neighbor)) {
                return true;
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    private Map<String, Set<String>> buildDependencyGraph(List<StepDefinition> stepsToValidate) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (StepDefinition step : stepsToValidate) {
            graph.put(step.id(), new HashSet<>(step.dependsOn()));
        }
        return graph;
    }
    
    public StepDefinition getStep(String stepId) {
        return steps.stream()
            .filter(s -> s.id().equals(stepId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));
    }
}
