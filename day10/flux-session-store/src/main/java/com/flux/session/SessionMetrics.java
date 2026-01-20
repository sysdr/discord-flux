package com.flux.session;

public record SessionMetrics(
    int totalSessions,
    int activeSessions,
    int idleSessions,
    int zombieSessions,
    long heapUsedMB,
    int totalCleanups
) {
    public String toJson() {
        return String.format(
            "{\"total\":%d,\"active\":%d,\"idle\":%d,\"zombie\":%d,\"heapMB\":%d,\"cleanups\":%d}",
            totalSessions, activeSessions, idleSessions, zombieSessions, heapUsedMB, totalCleanups
        );
    }
}
