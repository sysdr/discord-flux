package com.flux.grpc;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScyllaDBClient {
    private static volatile CqlSession session;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    
    private static PreparedStatement insertStmt;
    private static PreparedStatement selectStmt;
    private static PreparedStatement selectHistoryStmt;
    private static PreparedStatement deleteStmt;
    
    public static CqlSession getSession() {
        if (session == null || session.isClosed()) {
            synchronized (ScyllaDBClient.class) {
                if (session == null || session.isClosed()) {
                    initializeSession();
                }
            }
        }
        return session;
    }
    
    private static void initializeSession() {
        try {
            session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
                .withLocalDatacenter("datacenter1")
                .withKeyspace("flux_messages")
                .build();
            
            prepareStatements();
            initialized.set(true);
            
            System.out.println("[ScyllaDB] Session initialized successfully");
        } catch (Exception e) {
            System.err.println("[ScyllaDB] Failed to initialize: " + e.getMessage());
            // For demo purposes, we'll continue without crashing
        }
    }
    
    private static void prepareStatements() {
        insertStmt = session.prepare(
            "INSERT INTO messages (channel_id, message_id, author_id, content, timestamp) " +
            "VALUES (?, ?, ?, ?, ?)"
        );
        
        selectStmt = session.prepare(
            "SELECT * FROM messages WHERE channel_id = ? AND message_id = ?"
        );
        
        selectHistoryStmt = session.prepare(
            "SELECT * FROM messages WHERE channel_id = ? LIMIT ?"
        );
        
        deleteStmt = session.prepare(
            "DELETE FROM messages WHERE channel_id = ? AND message_id = ?"
        );
    }
    
    public static PreparedStatement getInsertStatement() {
        getSession(); // Ensure initialized
        return insertStmt;
    }
    
    public static PreparedStatement getSelectStatement() {
        getSession();
        return selectStmt;
    }
    
    public static PreparedStatement getSelectHistoryStatement() {
        getSession();
        return selectHistoryStmt;
    }
    
    public static PreparedStatement getDeleteStatement() {
        getSession();
        return deleteStmt;
    }
    
    public static boolean isConnected() {
        return session != null && !session.isClosed();
    }
    
    public static void shutdown() {
        if (session != null && !session.isClosed()) {
            session.close();
            System.out.println("[ScyllaDB] Session closed");
        }
    }
}
