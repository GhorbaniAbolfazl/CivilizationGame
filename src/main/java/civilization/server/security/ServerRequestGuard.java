package civilization.server.security;

import civilization.server.session.SessionId;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

// PHASE 3 - Added in Phase 3
public final class ServerRequestGuard {
    // PHASE 3 - Added in Phase 3
    public static final int DEFAULT_REQUESTS_PER_WINDOW = 64;
    // PHASE 3 - Added in Phase 3
    public static final Duration DEFAULT_RATE_WINDOW = Duration.ofSeconds(1);
    // PHASE 3 - Added in Phase 3
    public static final int DEFAULT_REPLAY_ENTRIES_PER_SESSION = 2_048;
    // PHASE 3 - Added in Phase 3
    public static final Duration DEFAULT_REPLAY_RETENTION = Duration.ofMinutes(2);

    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<SessionId, SessionWindow> windows =
            new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final int requestsPerWindow;
    // PHASE 3 - Added in Phase 3
    private final long rateWindowNanos;
    // PHASE 3 - Added in Phase 3
    private final int replayEntriesPerSession;
    // PHASE 3 - Added in Phase 3
    private final long replayRetentionNanos;
    // PHASE 3 - Added in Phase 3
    private final LongSupplier nanoTime;

    // PHASE 3 - Added in Phase 3
    public ServerRequestGuard() {
        this(DEFAULT_REQUESTS_PER_WINDOW, DEFAULT_RATE_WINDOW,
                DEFAULT_REPLAY_ENTRIES_PER_SESSION, DEFAULT_REPLAY_RETENTION,
                System::nanoTime);
    }

    // PHASE 3 - Added in Phase 3
    public ServerRequestGuard(int requestsPerWindow, Duration rateWindow,
                              int replayEntriesPerSession, Duration replayRetention,
                              LongSupplier nanoTime) {
        if (requestsPerWindow <= 0 || replayEntriesPerSession <= 0) {
            throw new IllegalArgumentException("Request guard limits must be positive");
        }
        this.requestsPerWindow = requestsPerWindow;
        this.rateWindowNanos = requirePositive(rateWindow, "rateWindow").toNanos();
        this.replayEntriesPerSession = replayEntriesPerSession;
        this.replayRetentionNanos = requirePositive(
                replayRetention, "replayRetention").toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    // PHASE 3 - Added in Phase 3
    public Decision inspect(SessionId sessionId, UUID messageId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(messageId, "messageId");
        long now = nanoTime.getAsLong();
        return windows.computeIfAbsent(sessionId, ignored -> new SessionWindow())
                .inspect(messageId, now);
    }

    // PHASE 3 - Added in Phase 3
    public void forget(SessionId sessionId) {
        windows.remove(Objects.requireNonNull(sessionId, "sessionId"));
    }

    // PHASE 3 - Added in Phase 3
    public void clear() {
        windows.clear();
    }

    // PHASE 3 - Added in Phase 3
    public int removeSessionsMatching(Predicate<SessionId> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        int before = windows.size();
        windows.keySet().removeIf(predicate);
        return before - windows.size();
    }

    // PHASE 3 - Added in Phase 3
    public int trackedSessionCount() {
        return windows.size();
    }

    // PHASE 3 - Added in Phase 3
    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    // PHASE 3 - Added in Phase 3
    public enum Decision {
        // PHASE 3 - Added in Phase 3
        ALLOW,
        // PHASE 3 - Added in Phase 3
        DUPLICATE,
        // PHASE 3 - Added in Phase 3
        RATE_LIMITED
    }

    // PHASE 3 - Added in Phase 3
    private final class SessionWindow {
        // PHASE 3 - Added in Phase 3
        private final ArrayDeque<Long> acceptedRequestTimes = new ArrayDeque<>();
        // PHASE 3 - Added in Phase 3
        private final LinkedHashMap<UUID, Long> acceptedMessageIds = new LinkedHashMap<>();

        // PHASE 3 - Added in Phase 3
        private synchronized Decision inspect(UUID messageId, long now) {
            prune(now);
            if (acceptedMessageIds.containsKey(messageId)) return Decision.DUPLICATE;
            if (acceptedRequestTimes.size() >= requestsPerWindow) {
                return Decision.RATE_LIMITED;
            }
            acceptedRequestTimes.addLast(now);
            acceptedMessageIds.put(messageId, now);
            // PHASE 3 - Added in Phase 3
            while (acceptedMessageIds.size() > replayEntriesPerSession) {
                Iterator<UUID> oldest = acceptedMessageIds.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            return Decision.ALLOW;
        }

        // PHASE 3 - Added in Phase 3
        private void prune(long now) {
            while (!acceptedRequestTimes.isEmpty()
                    && elapsed(now, acceptedRequestTimes.peekFirst()) >= rateWindowNanos) {
                acceptedRequestTimes.removeFirst();
            }
            Iterator<java.util.Map.Entry<UUID, Long>> entries =
                    acceptedMessageIds.entrySet().iterator();
            // PHASE 3 - Added in Phase 3
            while (entries.hasNext()) {
                if (elapsed(now, entries.next().getValue()) < replayRetentionNanos) break;
                entries.remove();
            }
        }

        // PHASE 3 - Added in Phase 3
        private long elapsed(long now, long earlier) {
            return now >= earlier ? now - earlier : Long.MAX_VALUE;
        }
    }
}
