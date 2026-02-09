package com.flux.automation;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

class WorkflowEngineTest {
    
    private ExecutionEngine engine;
    
    @BeforeEach
    void setUp() {
        engine = new ExecutionEngine();
    }
    
    @AfterEach
    void tearDown() {
        engine.shutdown();
    }
    
    @Test
    void testLinearWorkflowExecution() throws Exception {
        WorkflowDefinition workflow = new WorkflowDefinition(
            "test-linear",
            "Linear Test",
            "Three sequential steps",
            List.of(
                StepDefinition.builder("step1", "Step 1", StepType.DELAY)
                    .config("durationMs", "100")
                    .build(),
                StepDefinition.builder("step2", "Step 2", StepType.DELAY)
                    .dependsOn("step1")
                    .config("durationMs", "100")
                    .build(),
                StepDefinition.builder("step3", "Step 3", StepType.DELAY)
                    .dependsOn("step2")
                    .config("durationMs", "100")
                    .build()
            )
        );
        
        var result = engine.executeAsync(workflow);
        WorkflowExecution execution = result.future().get(5, TimeUnit.SECONDS);
        
        assertEquals(ExecutionStatus.COMPLETED, execution.getStatus());
        assertEquals(3, execution.getStepResults().size());
        assertTrue(execution.durationMs() >= 300);
    }
    
    @Test
    void testParallelWorkflowExecution() throws Exception {
        WorkflowDefinition workflow = new WorkflowDefinition(
            "test-parallel",
            "Parallel Test",
            "Three parallel steps",
            List.of(
                StepDefinition.builder("step1", "Step 1", StepType.DELAY)
                    .config("durationMs", "200")
                    .build(),
                StepDefinition.builder("step2", "Step 2", StepType.DELAY)
                    .config("durationMs", "200")
                    .build(),
                StepDefinition.builder("step3", "Step 3", StepType.DELAY)
                    .config("durationMs", "200")
                    .build()
            )
        );
        
        var result = engine.executeAsync(workflow);
        WorkflowExecution execution = result.future().get(5, TimeUnit.SECONDS);
        
        assertEquals(ExecutionStatus.COMPLETED, execution.getStatus());
        assertEquals(3, execution.getStepResults().size());
        assertTrue(execution.durationMs() < 400);
    }
    
    @Test
    void testCycleDetection() {
        assertThrows(IllegalArgumentException.class, () -> {
            new WorkflowDefinition(
                "test-cycle",
                "Cycle Test",
                "Should detect cycle",
                List.of(
                    StepDefinition.builder("step1", "Step 1", StepType.DELAY)
                        .dependsOn("step2")
                        .build(),
                    StepDefinition.builder("step2", "Step 2", StepType.DELAY)
                        .dependsOn("step1")
                        .build()
                )
            );
        });
    }
    
    @Test
    void testRetryLogic() throws Exception {
        WorkflowDefinition workflow = new WorkflowDefinition(
            "test-retry",
            "Retry Test",
            "Step that fails and retries",
            List.of(
                StepDefinition.builder("failing-step", "Failing Step", StepType.FAILING)
                    .retryAttempts(3)
                    .timeoutSeconds(5)
                    .config("durationMs", "100")
                    .build()
            )
        );
        
        var execResult = engine.executeAsync(workflow);
        WorkflowExecution execution = execResult.future().get(10, TimeUnit.SECONDS);
        
        assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        StepResult stepResult = execution.getStepResult("failing-step");
        assertEquals(3, stepResult.attemptNumber());
    }
    
    @Test
    void testTopologicalSort() {
        WorkflowDefinition workflow = new WorkflowDefinition(
            "test-topo",
            "Topology Test",
            "Complex dependencies",
            List.of(
                StepDefinition.builder("a", "A", StepType.DELAY).build(),
                StepDefinition.builder("b", "B", StepType.DELAY).dependsOn("a").build(),
                StepDefinition.builder("c", "C", StepType.DELAY).dependsOn("a").build(),
                StepDefinition.builder("d", "D", StepType.DELAY).dependsOn("b", "c").build()
            )
        );
        
        TopologicalExecutionPlanner planner = new TopologicalExecutionPlanner();
        var levels = planner.computeExecutionLevels(workflow);
        
        assertEquals(3, levels.size());
        assertEquals(1, levels.get(0).size());
        assertEquals(2, levels.get(1).size());
        assertEquals(1, levels.get(2).size());
    }
}
