package com.flux.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import java.net.InetSocketAddress;

public class FluxPersistenceApp {
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Starting Flux Day 35: Schema Design Demo");
        try (var session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
                .withLocalDatacenter("datacenter1")
                .withKeyspace("flux")
                .build()) {
            var dashboard = new DashboardServer(session, 8080);
            dashboard.start();
            System.out.println("✅ Application ready! Open dashboard: http://localhost:8080");
            Thread.currentThread().join();
        }
    }
}
