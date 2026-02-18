package com.flux.gateway.connection;

import com.flux.gateway.dashboard.MetricsCollector;
import com.flux.gateway.protocol.GatewayOpcode;
import com.flux.gateway.protocol.IdentifyPayload;
import com.flux.gateway.protocol.PayloadParser;
import com.flux.gateway.shard.ShardEnforcer;
import com.flux.gateway.shard.ShardRegistry;
import com.flux.gateway.shard.ShardSession;
import com.flux.gateway.websocket.WebSocketFrame;
import com.flux.gateway.websocket.WebSocketFrameParser;
import com.flux.gateway.websocket.WebSocketHandshake;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.nio.channels.Channels;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles the full lifecycle of one WebSocket connection.
 *
 * This class runs entirely within a single Virtual Thread.
 * It performs blocking I/O — which is correct in Java 21+:
 * the JVM unmounts the virtual thread from its carrier thread while
 * the OS waits for data, allowing the carrier to execute other virtual threads.
 *
 * State machine transitions are driven by received opcodes:
 *   HANDSHAKING → WAITING_IDENTIFY (on HTTP 101 sent)
 *   WAITING_IDENTIFY → IDENTIFYING → IDENTIFIED → READY
 *   any state → ZOMBIE/DISCONNECTED on error or closure
 */
public final class GatewayConnection implements Runnable {

    private static final int    HEARTBEAT_INTERVAL_MS = 41_250;
    private static final int    IDENTIFY_TIMEOUT_MS   = 10_000;
    private static final String CLOSE_GOING_AWAY       = "1001";

    private final long            connectionId;
    private final SocketChannel   channel;
    private final ShardRegistry   registry;
    private final MetricsCollector metrics;

    private final AtomicReference<ConnectionState> state =
        new AtomicReference<>(ConnectionState.HANDSHAKING);

    private ShardSession currentSession;

    public GatewayConnection(
            long connectionId,
            SocketChannel channel,
            ShardRegistry registry,
            MetricsCollector metrics
    ) {
        this.connectionId = connectionId;
        this.channel      = channel;
        this.registry     = registry;
        this.metrics      = metrics;
    }

    @Override
    public void run() {
        var remoteAddress = "unknown";
        try {
            remoteAddress = channel.getRemoteAddress().toString();
        } catch (IOException ignored) {}

        try (channel) {
            var in  = Channels.newInputStream(channel);
            var out = Channels.newOutputStream(channel);

            // ── Phase 1: WebSocket HTTP Upgrade ──────────────────────────
            WebSocketHandshake.perform(in, out);
            transition(ConnectionState.HANDSHAKING, ConnectionState.WAITING_IDENTIFY);

            // ── Phase 2: Send HELLO ───────────────────────────────────────
            WebSocketFrameParser.writeTextFrame(
                out, PayloadParser.buildHello(HEARTBEAT_INTERVAL_MS));

            // ── Phase 3: Wait for IDENTIFY (with timeout) ────────────────
            channel.socket().setSoTimeout(IDENTIFY_TIMEOUT_MS);
            processIdentify(in, out);
            channel.socket().setSoTimeout(0); // disable timeout after IDENTIFY

            // ── Phase 4: Steady-state frame loop ─────────────────────────
            runEventLoop(in, out);

        } catch (IOException e) {
            // Normal disconnection — log at debug level in production
            System.out.printf("[Conn %d] Disconnected from %s: %s%n",
                connectionId, remoteAddress, e.getMessage());
        } catch (Exception e) {
            System.err.printf("[Conn %d] Unexpected error: %s%n", connectionId, e.getMessage());
        } finally {
            cleanup();
        }
    }

    // ── IDENTIFY phase ────────────────────────────────────────────────────

    private void processIdentify(InputStream in, OutputStream out) throws IOException {
        var frame = WebSocketFrameParser.readFrame(in);

        if (frame.isClose()) {
            throw new IOException("Client sent CLOSE during IDENTIFY window");
        }
        if (!frame.isText()) {
            WebSocketFrameParser.writeCloseFrame(out, 1002); // Protocol Error
            throw new IOException("Expected text frame for IDENTIFY, got opcode " + frame.opcode());
        }

        var json = frame.textPayload();
        var opcodeOpt = PayloadParser.extractOpcode(json);

        if (opcodeOpt.isEmpty() || opcodeOpt.getAsInt() != GatewayOpcode.IDENTIFY.code) {
            WebSocketFrameParser.writeTextFrame(out, PayloadParser.buildInvalidSession(false));
            throw new IOException("Expected IDENTIFY (op=2), got: " + json);
        }

        // Parse and validate the IDENTIFY payload
        IdentifyPayload payload;
        try {
            payload = PayloadParser.parseIdentify(json);
        } catch (IllegalArgumentException e) {
            WebSocketFrameParser.writeTextFrame(out, PayloadParser.buildInvalidSession(false));
            metrics.incrementIdentifyParseErrors();
            throw new IOException("Malformed IDENTIFY payload: " + e.getMessage());
        }

        var validationResult = ShardEnforcer.validate(payload);
        switch (validationResult) {
            case ShardEnforcer.ValidationResult.Invalid inv -> {
                System.out.printf("[Conn %d] IDENTIFY rejected: %s%n",
                    connectionId, inv.reason());
                WebSocketFrameParser.writeTextFrame(
                    out, PayloadParser.buildInvalidSession(inv.resumable()));
                metrics.incrementIdentifyRejected();
                throw new IOException("IDENTIFY validation failed: " + inv.reason());
            }
            case ShardEnforcer.ValidationResult.Valid ignored -> { /* proceed */ }
        }

        transition(ConnectionState.WAITING_IDENTIFY, ConnectionState.IDENTIFYING);

        // Attempt to claim the shard slot in the registry
        var sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        var session   = new ShardSession(connectionId, payload.shardIdentity(), sessionId, channel);

        var claimResult = registry.claim(session);
        long identifyNanos = System.nanoTime();

        switch (claimResult) {
            case ShardRegistry.ClaimResult.Claimed c -> {
                currentSession = c.session();
                System.out.printf("[Conn %d] SHARD_READY %s session=%s%n",
                    connectionId, payload.shardIdentity(), sessionId);
                WebSocketFrameParser.writeTextFrame(out, PayloadParser.buildReady(
                    payload.shardIdentity().shardId(),
                    payload.shardIdentity().numShards(),
                    sessionId, false));
                metrics.incrementIdentifySuccess(System.nanoTime() - identifyNanos);
            }
            case ShardRegistry.ClaimResult.Evicted ev -> {
                currentSession = ev.session();
                System.out.printf("[Conn %d] SHARD_READY %s (evicted zombie session=%s) new=%s%n",
                    connectionId, payload.shardIdentity(),
                    ev.evicted().sessionId, sessionId);
                WebSocketFrameParser.writeTextFrame(out, PayloadParser.buildReady(
                    payload.shardIdentity().shardId(),
                    payload.shardIdentity().numShards(),
                    sessionId, true));
                metrics.incrementIdentifySuccess(System.nanoTime() - identifyNanos);
            }
            case ShardRegistry.ClaimResult.Rejected rej -> {
                System.out.printf("[Conn %d] SHARD_CONFLICT %s already owned by session=%s%n",
                    connectionId, payload.shardIdentity(), rej.existing().sessionId);
                WebSocketFrameParser.writeTextFrame(out, PayloadParser.buildInvalidSession(true));
                metrics.incrementIdentifyRejected();
                throw new IOException("Shard conflict — slot occupied by live session");
            }
        }

        transition(ConnectionState.IDENTIFYING, ConnectionState.IDENTIFIED);
        transition(ConnectionState.IDENTIFIED, ConnectionState.READY);
    }

    // ── Steady-state event loop ───────────────────────────────────────────

    private void runEventLoop(InputStream in, OutputStream out) throws IOException {
        while (!state.get().isTerminal()) {
            var frame = WebSocketFrameParser.readFrame(in);

            switch (frame.opcode()) {
                case WebSocketFrame.OPCODE_TEXT -> handleTextFrame(frame, out);
                case WebSocketFrame.OPCODE_PING -> WebSocketFrameParser.writeTextFrame(out, "");
                case WebSocketFrame.OPCODE_CLOSE -> {
                    WebSocketFrameParser.writeCloseFrame(out, 1000);
                    return;
                }
                default -> System.out.printf("[Conn %d] Ignoring opcode %d%n",
                    connectionId, frame.opcode());
            }
        }
    }

    private void handleTextFrame(WebSocketFrame frame, OutputStream out) throws IOException {
        var json      = frame.textPayload();
        var opcodeOpt = PayloadParser.extractOpcode(json);
        if (opcodeOpt.isEmpty()) return;

        var opcode = GatewayOpcode.fromCode(opcodeOpt.getAsInt());
        switch (opcode) {
            case HEARTBEAT -> {
                metrics.incrementHeartbeats();
                WebSocketFrameParser.writeTextFrame(out, PayloadParser.buildHeartbeatAck());
            }
            case IDENTIFY -> {
                // Re-IDENTIFY from READY state is a protocol violation
                WebSocketFrameParser.writeCloseFrame(out, 4005); // Already authenticated
                throw new IOException("Re-IDENTIFY from READY state — closing");
            }
            default -> { /* Other opcodes handled in future lessons */ }
        }
    }

    // ── Lifecycle helpers ─────────────────────────────────────────────────

    private void transition(ConnectionState expected, ConnectionState next) {
        if (!state.compareAndSet(expected, next)) {
            System.err.printf("[Conn %d] Illegal state transition: expected %s got %s%n",
                connectionId, expected, state.get());
        }
    }

    private void cleanup() {
        state.set(ConnectionState.DISCONNECTED);
        if (currentSession != null) {
            currentSession.transition(ConnectionState.READY, ConnectionState.ZOMBIE);
            registry.release(currentSession.identity, connectionId);
        }
        metrics.decrementActiveConnections();
    }
}
