package com.flux.automation;

import java.util.*;

/**
 * Computes execution order for workflow steps using topological sort.
 * Groups independent steps into "levels" that can execute in parallel.
 */
public class TopologicalExecutionPlanner {
    
    public List<Set<StepDefinition>> computeExecutionLevels(WorkflowDefinition workflow) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        Map<String, StepDefinition> stepMap = new HashMap<>();
        
        for (StepDefinition step : workflow.steps()) {
            stepMap.put(step.id(), step);
            inDegree.put(step.id(), step.dependsOn().size());
            
            for (String dependency : step.dependsOn()) {
                dependents.computeIfAbsent(dependency, k -> new ArrayList<>())
                    .add(step.id());
            }
        }
        
        Queue<String> ready = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.offer(entry.getKey());
            }
        }
        
        List<Set<StepDefinition>> levels = new ArrayList<>();
        while (!ready.isEmpty()) {
            Set<StepDefinition> currentLevel = new HashSet<>();
            int levelSize = ready.size();
            
            for (int i = 0; i < levelSize; i++) {
                String stepId = ready.poll();
                currentLevel.add(stepMap.get(stepId));
                
                for (String dependent : dependents.getOrDefault(stepId, List.of())) {
                    int newDegree = inDegree.merge(dependent, -1, Integer::sum);
                    if (newDegree == 0) {
                        ready.offer(dependent);
                    }
                }
            }
            
            levels.add(currentLevel);
        }
        
        int totalProcessed = levels.stream().mapToInt(Set::size).sum();
        if (totalProcessed != workflow.steps().size()) {
            throw new IllegalStateException("Workflow contains unreachable steps");
        }
        
        return levels;
    }
    
    public long calculateTheoreticalMinDuration(WorkflowDefinition workflow) {
        List<Set<StepDefinition>> levels = computeExecutionLevels(workflow);
        long totalDuration = 0;
        
        for (Set<StepDefinition> level : levels) {
            long levelDuration = level.stream()
                .mapToLong(step -> Integer.parseInt(
                    step.config().getOrDefault("durationMs", "1000")
                ))
                .max()
                .orElse(0);
            totalDuration += levelDuration;
        }
        
        return totalDuration;
    }
}
