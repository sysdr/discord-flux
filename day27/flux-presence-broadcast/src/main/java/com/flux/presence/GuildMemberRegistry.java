package com.flux.presence;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free registry of guild memberships.
 * Uses CopyOnWriteArrayList for member lists - optimized for read-heavy workload.
 * Reads (broadcasts) are lock-free and wait-free.
 * Writes (join/leave) are rare and can afford the copy cost.
 */
public class GuildMemberRegistry {
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<GatewayConnection>> guildMembers;
    private final AtomicLong totalMembers = new AtomicLong(0);
    
    public GuildMemberRegistry() {
        this.guildMembers = new ConcurrentHashMap<>();
    }
    
    /**
     * Add a connection to a guild.
     */
    public void addMember(long guildId, GatewayConnection connection) {
        guildMembers.computeIfAbsent(guildId, k -> new CopyOnWriteArrayList<>())
                   .add(connection);
        totalMembers.incrementAndGet();
    }
    
    /**
     * Remove a connection from a guild.
     */
    public void removeMember(long guildId, GatewayConnection connection) {
        CopyOnWriteArrayList<GatewayConnection> members = guildMembers.get(guildId);
        if (members != null) {
            if (members.remove(connection)) {
                totalMembers.decrementAndGet();
            }
            if (members.isEmpty()) {
                guildMembers.remove(guildId);
            }
        }
    }
    
    /**
     * Get all members of a guild (lock-free read).
     */
    public List<GatewayConnection> getGuildMembers(long guildId) {
        return guildMembers.getOrDefault(guildId, new CopyOnWriteArrayList<>());
    }
    
    /**
     * Get guild size.
     */
    public int getGuildSize(long guildId) {
        CopyOnWriteArrayList<GatewayConnection> members = guildMembers.get(guildId);
        return members != null ? members.size() : 0;
    }
    
    public long getTotalMembers() {
        return totalMembers.get();
    }
    
    public int getGuildCount() {
        return guildMembers.size();
    }
}
