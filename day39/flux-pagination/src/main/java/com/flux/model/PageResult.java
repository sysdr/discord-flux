package com.flux.model;

import java.util.List;

public record PageResult(
    List<Message> messages,
    String nextCursor,
    String previousCursor,
    boolean hasMore,
    long totalFetched,
    long queryLatencyMs
) {
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            sb.append(messages.get(i).toJson());
            if (i < messages.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"nextCursor\":").append(nextCursor == null ? "null" : "\"" + nextCursor + "\"").append(",");
        sb.append("\"previousCursor\":").append(previousCursor == null ? "null" : "\"" + previousCursor + "\"").append(",");
        sb.append("\"hasMore\":").append(hasMore).append(",");
        sb.append("\"totalFetched\":").append(totalFetched).append(",");
        sb.append("\"queryLatencyMs\":").append(queryLatencyMs);
        sb.append("}");
        return sb.toString();
    }
}
