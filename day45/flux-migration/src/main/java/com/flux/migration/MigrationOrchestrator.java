package com.flux.migration;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main orchestrator for zero-downtime migration.
 * Coordinates: Parser -> Writer -> Checkpoint with crash recovery.
 */
public class MigrationOrchestrator {
    
    private static final Logger log = LoggerFactory.getLogger(MigrationOrchestrator.class);
    
    private final CqlSession session;
    private final CheckpointManager checkpointManager;
    private final int maxConcurrentWrites;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Path metricsFile = Path.of("metrics.json");
    
    public MigrationOrchestrator(String cassandraHost, int port, int maxConcurrentWrites) throws IOException {
        this.maxConcurrentWrites = maxConcurrentWrites;
        
        log.info("Connecting to Cassandra at {}:{}...", cassandraHost, port);
        this.session = new CqlSessionBuilder()
            .addContactPoint(new InetSocketAddress(cassandraHost, port))
            .withLocalDatacenter("datacenter1")
            .build();
        
        initializeSchema();
        
        this.checkpointManager = new CheckpointManager(Path.of("migration.checkpoint"));
    }
    
    private void initializeSchema() {
        session.execute(
            """
            CREATE KEYSPACE IF NOT EXISTS flux
            WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
            """
        );
        
        session.execute(
            """
            CREATE TABLE IF NOT EXISTS flux.messages (
                channel_id bigint,
                timestamp timestamp,
                id bigint,
                user_id text,
                content text,
                PRIMARY KEY ((channel_id), timestamp, id)
            ) WITH CLUSTERING ORDER BY (timestamp DESC, id DESC)
            """
        );
        
        log.info("Schema initialized");
    }
    
    public void migrate(Path jsonFile) throws IOException {
        String filename = jsonFile.getFileName().toString();
        long lastOffset = checkpointManager.getLastOffset(filename);
        
        if (lastOffset > 0) {
            log.info("Resuming migration from offset {}", lastOffset);
        }
        
        Instant startTime = Instant.now();
        
        try (StreamingJsonParser parser = new StreamingJsonParser();
             CassandraWriter writer = new CassandraWriter(session, maxConcurrentWrites)) {
            
            parser.stream(jsonFile)
                .skip(lastOffset) // Resume from checkpoint
                .takeWhile(msg -> running.get())
                .forEach(msg -> {
                    writer.write(msg);
                    
                    long currentOffset = parser.getParsedCount();
                    long written = writer.getWrittenCount();
                    try {
                        if (currentOffset % 1_000 == 0) {
                            writeMetrics(parser.getParsedCount(), written, startTime);
                        }
                        if (currentOffset % 10_000 == 0) {
                            checkpointManager.recordProgress(filename, currentOffset);
                            log.info("Progress: {} messages (written: {}, errors: {})", 
                                     currentOffset, written, writer.getErrorCount());
                        }
                    } catch (IOException e) {
                        log.error("Checkpoint/metrics failed", e);
                    }
                });
            
            writeMetrics(parser.getParsedCount(), writer.getWrittenCount(), startTime);
            checkpointManager.recordProgress(filename, parser.getParsedCount());
            
        }
        
        Duration elapsed = Duration.between(startTime, Instant.now());
        log.info("Migration completed in {} seconds", elapsed.toSeconds());
    }
    
    private void writeMetrics(long parsed, long written, Instant startTime) throws IOException {
        long elapsedSec = Math.max(1, Duration.between(startTime, Instant.now()).toSeconds());
        long throughput = written / elapsedSec;
        long heapMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        String json = String.format(
            "{\"parsedMessages\":%d,\"writtenMessages\":%d,\"throughput\":%d,\"heapUsedMB\":%d,\"virtualThreads\":%d}",
            parsed, written, throughput, heapMB, maxConcurrentWrites);
        Files.writeString(metricsFile, json);
    }
    
    public void shutdown() {
        running.set(false);
        session.close();
        log.info("Orchestrator shutdown complete");
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java MigrationOrchestrator <json-file>");
            System.exit(1);
        }
        
        Path jsonFile = Path.of(args[0]);
        
        MigrationOrchestrator orchestrator = new MigrationOrchestrator(
            "localhost", 9042, 1000
        );
        
        Runtime.getRuntime().addShutdownHook(new Thread(orchestrator::shutdown));
        
        orchestrator.migrate(jsonFile);
    }
}
