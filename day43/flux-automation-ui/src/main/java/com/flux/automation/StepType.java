package com.flux.automation;

/**
 * Types of workflow steps that can be executed.
 */
public enum StepType {
    HTTP,        // Make HTTP request
    SCRIPT,      // Execute script
    DATABASE,    // Database operation
    EMAIL,       // Send email
    DELAY,       // Wait/sleep
    CONDITIONAL, // Conditional execution
    FAILING      // Intentionally fails (for testing)
}
