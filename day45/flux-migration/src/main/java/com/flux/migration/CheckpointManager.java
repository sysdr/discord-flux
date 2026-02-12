package com.flux.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Crash-safe checkpoint tracking using a simple properties file.
 * For production, use Chronicle Map or RocksDB for sub-microsecond latency.
 */
public class CheckpointManager {
    
    private static final Logger log = LoggerFactory.getLogger(CheckpointManager.class);
    
    private final Path checkpointFile;
    private final Properties checkpoints = new Properties();
    
    public CheckpointManager(Path checkpointFile) throws IOException {
        this.checkpointFile = checkpointFile;
        
        if (Files.exists(checkpointFile)) {
            checkpoints.load(Files.newInputStream(checkpointFile));
            log.info("Loaded checkpoint: {}", checkpoints);
        }
    }
    
    public void recordProgress(String filename, long recordsProcessed) throws IOException {
        checkpoints.setProperty(filename, String.valueOf(recordsProcessed));
        
        // Atomic write: write to temp, then rename
        Path temp = Path.of(checkpointFile + ".tmp");
        checkpoints.store(Files.newOutputStream(temp, 
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), 
            "Migration Checkpoint");
        
        Files.move(temp, checkpointFile, 
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }
    
    public long getLastOffset(String filename) {
        return Long.parseLong(checkpoints.getProperty(filename, "0"));
    }
}
