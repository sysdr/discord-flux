package com.flux.persistence;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import java.net.InetSocketAddress;

public class SchemaInitializer {
    private final CqlSession session;

    public SchemaInitializer(CqlSession session) { this.session = session; }

    public void initialize() {
        createKeyspace();
        useKeyspace();
        createMessagesTable();
        createMetricsTable();
        System.out.println("✅ Schema initialized successfully");
    }

    private void createKeyspace() {
        session.execute(SimpleStatement.newInstance("""
            CREATE KEYSPACE IF NOT EXISTS flux
            WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
            """));
        System.out.println("📦 Keyspace 'flux' created");
    }

    private void useKeyspace() { session.execute("USE flux"); }

    private void createMessagesTable() {
        session.execute(SimpleStatement.newInstance("""
            CREATE TABLE IF NOT EXISTS messages (
                channel_id BIGINT, bucket INT, message_id TIMEUUID, user_id BIGINT,
                content TEXT, created_at TIMESTAMP,
                PRIMARY KEY ((channel_id, bucket), message_id)
            ) WITH CLUSTERING ORDER BY (message_id DESC)
            AND compaction = {'class': 'TimeWindowCompactionStrategy',
                'compaction_window_unit': 'HOURS', 'compaction_window_size': 1}
            AND gc_grace_seconds = 86400
            """));
        System.out.println("📋 Table 'messages' created");
    }

    private void createMetricsTable() {
        session.execute(SimpleStatement.newInstance("""
            CREATE TABLE IF NOT EXISTS partition_metrics (
                channel_id BIGINT, bucket INT, message_count COUNTER,
                PRIMARY KEY (channel_id, bucket)
            )
            """));
        System.out.println("📊 Table 'partition_metrics' created");
    }

    public static void main(String[] args) {
        try (var session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
                .withLocalDatacenter("datacenter1")
                .build()) {
            new SchemaInitializer(session).initialize();
        }
    }
}
