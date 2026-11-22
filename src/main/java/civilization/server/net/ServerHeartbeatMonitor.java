package civilization.server.net;

import civilization.network.transport.HeartbeatListener;
import civilization.network.transport.HeartbeatLivenessTracker;
import civilization.network.transport.TcpMessageConnection;
import civilization.network.transport.UdpHeartbeatEndpoint;
import civilization.protocol.HeartbeatMessage;
import civilization.protocol.HeartbeatPing;
import civilization.protocol.HeartbeatPong;
import civilization.protocol.MessageMetadata;
import civilization.protocol.json.MessageSerializer;
import civilization.server.session.SessionId;
import java.io.IOException;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

// PHASE 3 - Added in Phase 3
public final class ServerHeartbeatMonitor implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    private final HeartbeatLivenessTracker tracker = new HeartbeatLivenessTracker();
    // PHASE 3 - Added in Phase 3
    private final Function<SessionId, TcpMessageConnection> connectionLookup;
    // PHASE 3 - Added in Phase 3
    private final Runnable maintenance;
    // PHASE 3 - Added in Phase 3
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "phase3-server-liveness"); thread.setDaemon(true); return thread;
    });
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<SessionId, Probe> probes = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<SessionId, Long> lastTcpConfirmationNanos =
            new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final AtomicLong probeSequence = new AtomicLong();
    // PHASE 3 - Added in Phase 3
    private UdpHeartbeatEndpoint endpoint;
    // PHASE 3 - Added in Phase 3
    private final Duration suspectAfter;
    // PHASE 3 - Added in Phase 3
    private final Duration probeTimeout;

    // PHASE 3 - Added in Phase 3
    public ServerHeartbeatMonitor(MessageSerializer serializer,
                                  Function<SessionId, TcpMessageConnection> connectionLookup,
                                  Runnable maintenance, Duration suspectAfter,
                                  Duration probeTimeout) {
        this.connectionLookup = Objects.requireNonNull(connectionLookup, "connectionLookup");
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance");
        this.suspectAfter = requirePositive(suspectAfter, "suspectAfter");
        this.probeTimeout = requirePositive(probeTimeout, "probeTimeout");
        endpoint = new UdpHeartbeatEndpoint(Objects.requireNonNull(serializer, "serializer"), new HeartbeatListener() {
            // PHASE 3 - Added in Phase 3
            @Override public void onHeartbeat(HeartbeatMessage message, SocketAddress remoteAddress) {
                if (!(message instanceof HeartbeatPing ping)) return;
                SessionId sessionId;
                try { sessionId = SessionId.parse(ping.sessionId()); }
                catch (RuntimeException ignored) { return; }
                if (ServerHeartbeatMonitor.this.connectionLookup.apply(sessionId) == null) return;
                tracker.record(ping.sessionId(), ping.sequence());
                // PHASE 3 - Added in Phase 3
                try { endpoint.send(new HeartbeatPong(MessageMetadata.createNow(), ping.sessionId(), ping.sequence()), remoteAddress); }
                catch (IOException | RuntimeException ignored) { /* a later datagram or TCP probe can establish liveness */ }
            }
            // PHASE 3 - Added in Phase 3
            @Override public void onTransportFailure(Throwable cause) { /* UDP failure cannot directly disconnect a player */ }
        });
    }

    // PHASE 3 - Added in Phase 3
    public void start(int port, Duration scanInterval) throws IOException {
        Duration interval = requirePositive(scanInterval, "scanInterval"); endpoint.start(port);
        scheduler.scheduleAtFixedRate(this::scanSafely, interval.toNanos(), interval.toNanos(), TimeUnit.NANOSECONDS);
    }

    // PHASE 3 - Added in Phase 3
    public int localPort() { return endpoint.localPort(); }

    // PHASE 3 - Added in Phase 3
    public void register(SessionId sessionId) {
        tracker.reset(sessionId.toString()); probes.remove(sessionId);
        // PHASE 3 - Added in Phase 3
        lastTcpConfirmationNanos.put(sessionId, System.nanoTime());
    }

    // PHASE 3 - Added in Phase 3
    public void unregister(SessionId sessionId) {
        tracker.unregister(sessionId.toString()); probes.remove(sessionId);
        // PHASE 3 - Added in Phase 3
        lastTcpConfirmationNanos.remove(sessionId);
    }

    // PHASE 3 - Added in Phase 3
    public boolean acceptTcpPong(SessionId sessionId, HeartbeatPong pong) {
        Probe probe = probes.get(sessionId);
        if (probe == null || probe.sequence() != pong.sequence() || !pong.sessionId().equals(sessionId.toString())) return false;
        if (!probes.remove(sessionId, probe)) return false;
        tracker.markAlive(sessionId.toString());
        // PHASE 3 - Added in Phase 3
        lastTcpConfirmationNanos.put(sessionId, System.nanoTime());
        return true;
    }

    // PHASE 3 - Added in Phase 3
    private void scanSafely() {
        try { scan(); maintenance.run(); }
        catch (RuntimeException ignored) { /* liveness maintenance must never terminate its scheduler */ }
    }

    // PHASE 3 - Added in Phase 3
    private void scan() {
        long now = System.nanoTime();
        // PHASE 3 - Added in Phase 3
        for (var confirmation : lastTcpConfirmationNanos.entrySet()) {
            SessionId sessionId = confirmation.getKey();
            String rawSessionId = sessionId.toString();
            TcpMessageConnection connection = connectionLookup.apply(sessionId);
            if (connection == null || !connection.isOpen()) { unregister(sessionId); continue; }
            Probe existing = probes.get(sessionId);
            // PHASE 3 - Added in Phase 3
            if (existing == null && now - confirmation.getValue() >= suspectAfter.toNanos()) {
                long sequence = probeSequence.incrementAndGet();
                Probe probe = new Probe(sequence, now);
                if (probes.putIfAbsent(sessionId, probe) == null) {
                    try { connection.send(new HeartbeatPing(MessageMetadata.createNow(), rawSessionId, sequence)); }
                    catch (RuntimeException failure) { connection.close(); }
                }
            } else if (existing != null && now - existing.sentAtNanos() >= probeTimeout.toNanos()
                    && probes.remove(sessionId, existing)) {
                connection.close();
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    public int trackedSessionCount() { return tracker.trackedSessionCount(); }
    // PHASE 3 - Added in Phase 3
    public int pendingProbeCount() { return probes.size(); }

    // PHASE 3 - Added in Phase 3
    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    // PHASE 3 - Added in Phase 3
    @Override public void close() {
        endpoint.close(); scheduler.shutdownNow(); probes.clear();
        // PHASE 3 - Added in Phase 3
        lastTcpConfirmationNanos.clear();
    }

    // PHASE 3 - Added in Phase 3
    private record Probe(long sequence, long sentAtNanos) {}
}
