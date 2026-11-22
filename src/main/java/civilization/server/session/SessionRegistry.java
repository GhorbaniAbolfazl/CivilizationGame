package civilization.server.session;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import civilization.network.transport.ConnectionId;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

// PHASE 3 - Added in Phase 3
public final class SessionRegistry {
    // PHASE 3 - Added in Phase 3
    public static final int DEFAULT_MAX_SESSIONS = 1_024;
    // PHASE 3 - Added in Phase 3
    public static final Duration DEFAULT_RECONNECT_WINDOW = Duration.ofSeconds(30);
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<SessionId, ClientSession> sessions =
            new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final Clock clock;
    // PHASE 3 - Added in Phase 3
    private final Duration reconnectWindow;
    // PHASE 3 - Added in Phase 3
    private final Semaphore sessionSlots;

    // PHASE 3 - Added in Phase 3
    public SessionRegistry() {
        this(Clock.systemUTC(), DEFAULT_RECONNECT_WINDOW, DEFAULT_MAX_SESSIONS);
    }

    // PHASE 3 - Added in Phase 3
    public SessionRegistry(Clock clock) {
        this(clock, DEFAULT_RECONNECT_WINDOW, DEFAULT_MAX_SESSIONS);
    }

    // PHASE 3 - Added in Phase 3
    public SessionRegistry(Clock clock, Duration reconnectWindow) {
        this(clock, reconnectWindow, DEFAULT_MAX_SESSIONS);
    }

    // PHASE 3 - Added in Phase 3
    public SessionRegistry(Clock clock, Duration reconnectWindow, int maxSessions) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reconnectWindow = Objects.requireNonNull(reconnectWindow, "reconnectWindow");
        if (reconnectWindow.isNegative()) throw new IllegalArgumentException("Reconnect window must not be negative");
        if (maxSessions <= 0) throw new IllegalArgumentException("Maximum sessions must be positive");
        sessionSlots = new Semaphore(maxSessions);
    }

    // PHASE 3 - Added in Phase 3
    public ClientSession create(String username) {
        // PHASE 3 - Added in Phase 3
        if (!sessionSlots.tryAcquire()) {
            throw new SessionException("SERVER_CAPACITY",
                    "Server session capacity has been reached");
        }
        // PHASE 3 - Added in Phase 3
        try {
            ClientSession session = new ClientSession(
                    SessionId.create(), PlayerId.create(), username, Instant.now(clock));
            ClientSession previous = sessions.putIfAbsent(session.sessionId(), session);
            if (previous != null) throw new IllegalStateException("Duplicate session ID");
            return session;
        } catch (RuntimeException failure) {
            sessionSlots.release();
            throw failure;
        }
    }

    // PHASE 3 - Added in Phase 3
    public Optional<ClientSession> find(SessionId sessionId) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(sessionId, "sessionId")));
    }

    // PHASE 3 - Added in Phase 3
    public ClientSession requireConnected(SessionId sessionId) {
        ClientSession session = find(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown session"));
        if (session.status() != SessionStatus.CONNECTED) {
            throw new IllegalStateException("Session is not connected");
        }
        return session;
    }

    // PHASE 3 - Added in Phase 3
    public boolean markDisconnected(SessionId sessionId) {
        return find(sessionId).map(ClientSession::markDisconnected).orElse(false);
    }

    // PHASE 3 - Added in Phase 3
    public ClientSession resume(String sessionId, String resumeSecret, ConnectionId connectionId) {
        ClientSession session = find(SessionId.parse(sessionId))
                .orElseThrow(() -> new SessionException("UNKNOWN_SESSION", "Session does not exist"));
        session.resume(resumeSecret, connectionId, Instant.now(clock), reconnectWindow);
        return session;
    }

    // PHASE 3 - Added in Phase 3
    public boolean disconnect(SessionId sessionId, ConnectionId connectionId) {
        return find(sessionId).map(session -> session.unbindConnection(connectionId, Instant.now(clock))).orElse(false);
    }

    // PHASE 3 - Added in Phase 3
    public int expireReconnectWindows() {
        Instant now = Instant.now(clock);
        return (int) sessions.values().stream()
                .filter(session -> session.expireReconnectWindow(now, reconnectWindow)).count();
    }

    // PHASE 3 - Added in Phase 3
    public int purgeClosedSessions() {
        int[] removed = {0};
        // PHASE 3 - Added in Phase 3
        sessions.forEach((sessionId, session) -> {
            if (session.status() == SessionStatus.CLOSED
                    && sessions.remove(sessionId, session)) {
                sessionSlots.release();
                removed[0]++;
            }
        });
        return removed[0];
    }

    // PHASE 3 - Added in Phase 3
    public Duration reconnectWindow() {
        return reconnectWindow;
    }

    // PHASE 3 - Added in Phase 3
    public boolean remove(SessionId sessionId) {
        ClientSession removed = sessions.remove(Objects.requireNonNull(sessionId, "sessionId"));
        if (removed == null) return false;
        removed.close();
        // PHASE 3 - Added in Phase 3
        sessionSlots.release();
        return true;
    }

    // PHASE 3 - Added in Phase 3
    public Collection<ClientSession> snapshot() {
        return List.copyOf(sessions.values());
    }
}
