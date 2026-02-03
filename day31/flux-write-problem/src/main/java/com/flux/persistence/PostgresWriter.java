package com.flux.persistence;

import com.flux.core.Message;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Postgres persistence using JDBC batching.
 */
public class PostgresWriter implements AutoCloseable {
    
    private final HikariDataSource dataSource;
    private final AtomicLong writtenCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    
    public PostgresWriter(String jdbcUrl, String username, String password) {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC driver not found. Add postgresql dependency to pom.xml.", e);
        }
        var config = new HikariConfig();
        config.setDriverClassName("org.postgresql.Driver");
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(10);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        this.dataSource = new HikariDataSource(config);
        initSchema();
    }
    
    private void initSchema() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id BIGINT PRIMARY KEY,
                    channel_id VARCHAR(64) NOT NULL,
                    content TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
            """);
            
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_messages_channel_time 
                ON messages(channel_id, created_at DESC)
            """);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize schema", e);
        }
    }
    
    public void writeBatch(List<Message> messages) throws SQLException {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(
                 "INSERT INTO messages (id, channel_id, content, created_at) VALUES (?, ?, ?, ?)")) {
            
            for (var msg : messages) {
                stmt.setLong(1, msg.id());
                stmt.setString(2, msg.channelId());
                stmt.setString(3, msg.content());
                stmt.setTimestamp(4, Timestamp.from(msg.createdAt()));
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            writtenCount.addAndGet(messages.size());
            
        } catch (SQLException e) {
            errorCount.incrementAndGet();
            throw e;
        }
    }
    
    public long getWrittenCount() {
        return writtenCount.get();
    }
    
    public long getErrorCount() {
        return errorCount.get();
    }
    
    public void reset() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE messages");
            writtenCount.set(0);
            errorCount.set(0);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset", e);
        }
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
