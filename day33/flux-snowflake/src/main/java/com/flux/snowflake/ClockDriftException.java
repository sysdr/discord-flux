package com.flux.snowflake;

/**
 * Thrown when system clock moves backwards beyond acceptable threshold.
 */
public class ClockDriftException extends RuntimeException {
    public ClockDriftException(String message) {
        super(message);
    }
}
