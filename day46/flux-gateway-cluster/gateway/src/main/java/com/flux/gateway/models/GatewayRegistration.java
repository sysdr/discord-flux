package com.flux.gateway.models;

import java.time.Instant;

public record GatewayRegistration(
    String nodeId,
    String ipAddress,
    int port,
    int currentConnections,
    Instant lastHeartbeat,
    String status
) {
    public String toJson() {
        return """
            {"nodeId":"%s","ipAddress":"%s","port":%d,"currentConnections":%d,"lastHeartbeat":%d,"status":"%s"}
            """.formatted(nodeId, ipAddress, port, currentConnections, 
                          lastHeartbeat.getEpochSecond(), status);
    }
    
    public static GatewayRegistration fromJson(String json) {
        // Simple JSON parsing without external libs for this specific format
        var nodeId = extractValue(json, "nodeId");
        var ipAddress = extractValue(json, "ipAddress");
        var port = Integer.parseInt(extractValue(json, "port"));
        var currentConnections = Integer.parseInt(extractValue(json, "currentConnections"));
        var heartbeat = Long.parseLong(extractValue(json, "lastHeartbeat"));
        var status = extractValue(json, "status");
        
        return new GatewayRegistration(
            nodeId, ipAddress, port, currentConnections,
            Instant.ofEpochSecond(heartbeat), status
        );
    }
    
    private static String extractValue(String json, String key) {
        var pattern = "\"" + key + "\":\"?([^,}\"]+)\"?";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }
}
