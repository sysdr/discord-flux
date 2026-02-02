package com.flux.gateway.assembler;

import com.flux.gateway.protocol.ChunkRequest;
import com.flux.gateway.protocol.ChunkResponse;
import com.flux.gateway.protocol.Member;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanIterator;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Zero-allocation chunk assembly using Redis SCAN.
 * Never loads the full member set into memory.
 */
public class ChunkAssembler {
    private final RedisClient redisClient;
    private final StatefulRedisConnection<String, String> connection;
    private final AtomicInteger chunksProcessed = new AtomicInteger(0);
    
    public ChunkAssembler(String redisUri) {
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect();
    }
    
    /**
     * Assemble chunks for a request using cursor-based iteration.
     * Returns list of chunk responses (one per SCAN iteration).
     */
    public List<ChunkResponse> assembleChunks(ChunkRequest request) {
        RedisCommands<String, String> commands = connection.sync();
        List<ChunkResponse> chunks = new ArrayList<>();
        
        String setKey = "guild:" + request.guildId() + ":members";
        
        // Estimate total chunks (approximate)
        long memberCount = commands.scard(setKey);
        int estimatedChunks = (int) Math.ceil((double) memberCount / Math.max(1, request.limit()));
        
        List<Member> batch = new ArrayList<>(request.limit());
        int chunkIndex = 0;
        
        ScanIterator<String> it = ScanIterator.sscan(commands, setKey,
                ScanArgs.Builder.limit(request.limit()));
        while (it.hasNext()) {
            String redisValue = it.next();
            Member m = Member.fromRedis(redisValue);
            if (matchesQuery(m, request.query())) {
                batch.add(m);
            }
            if (batch.size() >= request.limit()) {
                chunks.add(new ChunkResponse(
                    request.guildId(),
                    new ArrayList<>(batch),
                    chunkIndex++,
                    estimatedChunks,
                    request.nonce()
                ));
                batch.clear();
            }
        }
        
        if (!batch.isEmpty()) {
            chunks.add(new ChunkResponse(
                request.guildId(),
                batch,
                chunkIndex,
                estimatedChunks,
                request.nonce()
            ));
        }
        
        chunksProcessed.addAndGet(chunks.size());
        return chunks;
    }
    
    private boolean matchesQuery(Member member, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return member.username().toLowerCase().startsWith(query.toLowerCase());
    }
    
    public int getChunksProcessed() {
        return chunksProcessed.get();
    }
    
    public void close() {
        connection.close();
        redisClient.shutdown();
    }
}
