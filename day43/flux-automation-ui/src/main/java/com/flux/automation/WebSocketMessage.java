package com.flux.automation;

public record WebSocketMessage(
    String type,
    String executionId,
    String data
) {
    public String toJson() {
        return String.format(
            "{\"type\":\"%s\",\"executionId\":\"%s\",\"data\":%s}",
            type, executionId, data
        );
    }
}
