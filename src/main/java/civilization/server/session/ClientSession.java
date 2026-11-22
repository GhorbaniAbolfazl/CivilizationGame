package civilization.server.session;

import civilization.network.transport.ConnectionId;

import java.time.Instant;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

// PHASE 3 - Added in Phase 3
public final class ClientSession {
    // PHASE 3 - Added in Phase 3
    private final SessionId sessionId;
    // PHASE 3 - Added in Phase 3
    private final PlayerId playerId;
    // PHASE 3 - Added in Phase 3
    private final String username;
    // PHASE 3 - Added in Phase 3
    private final Instant createdAt;
    // PHASE 3 - Added in Phase 3
    private final AtomicReference<SessionStatus> status =
            new AtomicReference<>(SessionStatus.CONNECTED);
    // PHASE 3 - Added in Phase 3
    private final AtomicReference<ConnectionId> activeConnection = new AtomicReference<>();
    // PHASE 3 - Added in Phase 3
    private final AtomicLong connectionGeneration = new AtomicLong();
    // PHASE 3 - Added in Phase 3
    private final String resumeSecret = createResumeSecret();
    // PHASE 3 - Added in Phase 3
    private final AtomicReference<Instant> disconnectedAt = new AtomicReference<>();

    // PHASE 3 - Added in Phase 3
    public ClientSession(
            SessionId sessionId, PlayerId playerId, String username, Instant createdAt) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.username = Objects.requireNonNull(username, "username");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (username.isBlank()) throw new IllegalArgumentException("Username must not be blank");
    }

    // PHASE 3 - Added in Phase 3
    public SessionId sessionId() {
        return sessionId;
    }

    // PHASE 3 - Added in Phase 3
    public PlayerId playerId() {
        return playerId;
    }

    // PHASE 3 - Added in Phase 3
    public String username() {
        return username;
    }

    // PHASE 3 - Added in Phase 3
    public Instant createdAt() {
        return createdAt;
    }

    // PHASE 3 - Added in Phase 3
    public SessionStatus status() {
        return status.get();
    }

    // PHASE 3 - Added in Phase 3
    public synchronized long bindConnection(ConnectionId connectionId) {
        ConnectionId trustedConnectionId = Objects.requireNonNull(connectionId, "connectionId");
        if (status.get() == SessionStatus.CLOSED) {
            throw new IllegalStateException("Closed session cannot bind a connection");
        }
        if (trustedConnectionId.equals(activeConnection.get())) {
            return connectionGeneration.get();
        }
        activeConnection.set(trustedConnectionId);
        status.set(SessionStatus.CONNECTED);
        disconnectedAt.set(null);
        return connectionGeneration.incrementAndGet();
    }

    // PHASE 3 - Added in Phase 3
    public synchronized boolean unbindConnection(ConnectionId connectionId) {
        return unbindConnection(connectionId, Instant.now());
    }

    // PHASE 3 - Added in Phase 3
    public synchronized boolean unbindConnection(ConnectionId connectionId, Instant detectedAt) {
        ConnectionId trustedConnectionId = Objects.requireNonNull(connectionId, "connectionId");
        Objects.requireNonNull(detectedAt, "detectedAt");
        if (!trustedConnectionId.equals(activeConnection.get())) return false;
        activeConnection.set(null);
        if (status.get() != SessionStatus.CLOSED) {
            status.set(SessionStatus.DISCONNECTED);
            disconnectedAt.set(detectedAt);
        }
        return true;
    }

    // PHASE 3 - Added in Phase 3
    public Optional<ConnectionId> activeConnection() {
        return Optional.ofNullable(activeConnection.get());
    }

    // PHASE 3 - Added in Phase 3
    public long connectionGeneration() {
        return connectionGeneration.get();
    }

    // PHASE 3 - Added in Phase 3
    public String resumeSecret() {
        return resumeSecret;
    }

    // PHASE 3 - Added in Phase 3
    public Optional<Instant> disconnectedAt() {
        return Optional.ofNullable(disconnectedAt.get());
    }

    // PHASE 3 - Added in Phase 3
    public synchronized long resume(String suppliedSecret, ConnectionId connectionId,
                                    Instant now, Duration graceWindow) {
        Objects.requireNonNull(suppliedSecret, "suppliedSecret");
        Objects.requireNonNull(connectionId, "connectionId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(graceWindow, "graceWindow");
        // PHASE 3 - Added in Phase 3
        if (status.get() == SessionStatus.CONNECTED) {
            throw new SessionException("SESSION_ALREADY_CONNECTED", "Session already has an active connection");
        }
        if (status.get() == SessionStatus.CLOSED) {
            throw new SessionException("RESUME_WINDOW_EXPIRED", "Session reconnect window has expired");
        }
        byte[] expected = resumeSecret.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedSecret.getBytes(StandardCharsets.UTF_8);
        // PHASE 3 - Added in Phase 3
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new SessionException("INVALID_RESUME_SECRET", "Session resume credentials are invalid");
        }
        Instant disconnected = disconnectedAt.get();
        if (disconnected == null || !now.isBefore(disconnected.plus(graceWindow))) {
            status.set(SessionStatus.CLOSED);
            throw new SessionException("RESUME_WINDOW_EXPIRED", "Session reconnect window has expired");
        }
        activeConnection.set(connectionId);
        disconnectedAt.set(null);
        status.set(SessionStatus.CONNECTED);
        return connectionGeneration.incrementAndGet();
    }

    // PHASE 3 - Added in Phase 3
    public synchronized boolean expireReconnectWindow(Instant now, Duration graceWindow) {
        Objects.requireNonNull(now, "now"); Objects.requireNonNull(graceWindow, "graceWindow");
        Instant disconnected = disconnectedAt.get();
        if (status.get() != SessionStatus.DISCONNECTED || disconnected == null
                || now.isBefore(disconnected.plus(graceWindow))) return false;
        status.set(SessionStatus.CLOSED); disconnectedAt.set(null); return true;
    }

    // PHASE 3 - Added in Phase 3
    public boolean markDisconnected() {
        boolean changed = status.compareAndSet(SessionStatus.CONNECTED,
                SessionStatus.DISCONNECTED);
        // PHASE 3 - Added in Phase 3
        if (changed) { activeConnection.set(null); disconnectedAt.set(Instant.now()); }
        return changed;
    }

    // PHASE 3 - Added in Phase 3
    public void close() {
        // PHASE 3 - Added in Phase 3
        activeConnection.set(null);
        disconnectedAt.set(null);
        status.set(SessionStatus.CLOSED);
    }

    // PHASE 3 - Added in Phase 3
    private static String createResumeSecret() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
