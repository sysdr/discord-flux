package com.flux.gateway.concurrency.common;

public interface ServerInterface extends AutoCloseable {
    void start() throws Exception;
    void stop();
    ServerMetrics getMetrics();
    int getPort();
    String getType();
}
