package com.flux.gateway.protocol;

import java.util.List;

/**
 * Opcode 9: Guild Members Chunk
 * Server response containing a slice of members.
 */
public record ChunkResponse(
    String guildId,
    List<Member> members,
    int chunkIndex,
    int chunkCount,
    String nonce
) {
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"op\":9,\"d\":{");
        sb.append("\"guild_id\":\"").append(guildId).append("\",");
        sb.append("\"chunk_index\":").append(chunkIndex).append(",");
        sb.append("\"chunk_count\":").append(chunkCount).append(",");
        sb.append("\"nonce\":\"").append(nonce).append("\",");
        sb.append("\"members\":[");
        
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(members.get(i).toJson());
        }
        
        sb.append("]}}");
        return sb.toString();
    }
}
