package com.flux.gateway.shard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ShardRegistry")
class ShardRegistryTest {

    private ShardRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ShardRegistry();
    }

    private ShardSession makeSession(int shardId, int numShards, SocketChannel ch) {
        var identity = new ShardIdentity(shardId, numShards);
        return new ShardSession(1L, identity, UUID.randomUUID().toString(), ch);
    }

    @Test
    @DisplayName("First claim for a shard slot returns Claimed")
    void firstClaimSucceeds() throws Exception {
        try (var pair = SocketPair.open()) {
            var session = makeSession(0, 16, pair.client());
            var result  = registry.claim(session);
            assertInstanceOf(ShardRegistry.ClaimResult.Claimed.class, result);
        }
    }

    @Test
    @DisplayName("Second claim for occupied live slot returns Rejected")
    void secondClaimRejected() throws Exception {
        try (var pair = SocketPair.open()) {
            var s1 = makeSession(0, 16, pair.client());
            var s2 = makeSession(0, 16, pair.server());

            registry.claim(s1);
            var result = registry.claim(s2);
            assertInstanceOf(ShardRegistry.ClaimResult.Rejected.class, result);
        }
    }

    @Test
    @DisplayName("Claim for zombie slot evicts and returns Evicted")
    void zombieSlotEvicted() throws Exception {
        try (var pair = SocketPair.open()) {
            var s1 = makeSession(0, 16, pair.client());
            registry.claim(s1);

            // Mark as zombie by closing the channel
            pair.client().close();

            // Open a fresh channel for the new session
            try (var pair2 = SocketPair.open()) {
                var s2     = makeSession(0, 16, pair2.client());
                var result = registry.claim(s2);
                assertInstanceOf(ShardRegistry.ClaimResult.Evicted.class, result);
            }
        }
    }

    @Test
    @DisplayName("Different shard IDs can be claimed independently")
    void differentShardsNonConflicting() throws Exception {
        try (var p1 = SocketPair.open(); var p2 = SocketPair.open()) {
            var s1 = makeSession(0, 16, p1.client());
            var s2 = makeSession(1, 16, p2.client());
            assertInstanceOf(ShardRegistry.ClaimResult.Claimed.class, registry.claim(s1));
            assertInstanceOf(ShardRegistry.ClaimResult.Claimed.class, registry.claim(s2));
            assertEquals(2, registry.activeCount());
        }
    }

    @Test
    @DisplayName("release() removes session by connectionId guard")
    void releaseRemovesCorrectSession() throws Exception {
        try (var pair = SocketPair.open()) {
            var identity = new ShardIdentity(0, 16);
            var session  = new ShardSession(42L, identity, "sess1", pair.client());
            registry.claim(session);
            assertTrue(registry.release(identity, 42L));
            assertEquals(0, registry.activeCount());
        }
    }

    @Test
    @DisplayName("release() with wrong connectionId does not remove newer session")
    void releaseDoesNotEvictNewerSession() throws Exception {
        try (var p1 = SocketPair.open(); var p2 = SocketPair.open()) {
            var identity = new ShardIdentity(0, 16);
            // Register session with id=100
            var old = new ShardSession(100L, identity, "old", p1.client());
            registry.claim(old);
            // Evict and replace with new session id=200
            p1.client().close();
            var fresh = new ShardSession(200L, identity, "new", p2.client());
            registry.claim(fresh); // Evicted result — new session registered
            // Old connection cleanup tries to release with its id=100 — must not remove session 200
            assertFalse(registry.release(identity, 100L));
            assertEquals(1, registry.activeCount());
        }
    }

    // Helper: creates a connected SocketChannel pair via loopback
    record SocketPair(SocketChannel client, SocketChannel server) implements AutoCloseable {
        static SocketPair open() throws IOException {
            try (var serverSocket = ServerSocketChannel.open()) {
                serverSocket.bind(new InetSocketAddress("localhost", 0));
                var port   = ((InetSocketAddress) serverSocket.getLocalAddress()).getPort();
                var client = SocketChannel.open(new InetSocketAddress("localhost", port));
                var server = serverSocket.accept();
                return new SocketPair(client, server);
            }
        }
        @Override public void close() throws IOException {
            client.close();
            server.close();
        }
    }
}
