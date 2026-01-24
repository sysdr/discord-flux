package com.flux.gateway;

import java.util.zip.CRC32;

/**
 * Routes guilds to gateway instances using consistent hashing.
 */
public class GuildRouter {
    private final int gatewayCount;
    private final int currentInstance;
    
    public GuildRouter(int gatewayCount, int currentInstance) {
        this.gatewayCount = gatewayCount;
        this.currentInstance = currentInstance;
    }
    
    /**
     * Determine if this gateway instance should handle the given guild.
     */
    public boolean shouldHandle(String guildId) {
        int targetInstance = hashToInstance(guildId);
        return targetInstance == currentInstance;
    }
    
    private int hashToInstance(String guildId) {
        CRC32 crc = new CRC32();
        crc.update(guildId.getBytes());
        long hash = crc.getValue();
        return (int) (hash % gatewayCount);
    }
    
    public String streamKey(String guildId) {
        return "guild:" + guildId + ":messages";
    }
}
