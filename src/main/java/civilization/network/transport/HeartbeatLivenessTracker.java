package civilization.network.transport;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

// PHASE 3 - Added in Phase 3
public final class HeartbeatLivenessTracker {
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<String, Observation> observations =
            new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final LongSupplier nanoTime;

    // PHASE 3 - Added in Phase 3
    public HeartbeatLivenessTracker() {
        this(System::nanoTime);
    }

    // PHASE 3 - Added in Phase 3
    public HeartbeatLivenessTracker(LongSupplier nanoTime) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    // PHASE 3 - Added in Phase 3
    public void register(String sessionId) {
        String trustedSessionId = requireSessionId(sessionId);
        observations.putIfAbsent(trustedSessionId,
                new Observation(-1L, nanoTime.getAsLong()));
    }

    // PHASE 3 - Added in Phase 3
    public void reset(String sessionId) {
        observations.put(requireSessionId(sessionId), new Observation(-1L, nanoTime.getAsLong()));
    }

    // PHASE 3 - Added in Phase 3
    public boolean markAlive(String sessionId) {
        String trustedSessionId = requireSessionId(sessionId);
        boolean[] changed = {false};
        // PHASE 3 - Added in Phase 3
        observations.computeIfPresent(trustedSessionId, (ignored, previous) -> {
            changed[0] = true; return new Observation(previous.sequence(), nanoTime.getAsLong());
        });
        return changed[0];
    }

    // PHASE 3 - Added in Phase 3
    public boolean record(String sessionId, long sequence) {
        String trustedSessionId = requireSessionId(sessionId);
        boolean[] accepted = {false};
        // PHASE 3 - Added in Phase 3
        observations.computeIfPresent(trustedSessionId, (ignored, previous) -> {
            if (sequence <= previous.sequence()) return previous;
            accepted[0] = true;
            return new Observation(sequence, nanoTime.getAsLong());
        });
        return accepted[0];
    }

    // PHASE 3 - Added in Phase 3
    public boolean unregister(String sessionId) {
        return observations.remove(requireSessionId(sessionId)) != null;
    }

    // PHASE 3 - Added in Phase 3
    public Set<String> staleSessions(Duration timeout) {
        long timeoutNanos = requirePositive(timeout).toNanos();
        long now = nanoTime.getAsLong();
        // PHASE 3 - Added in Phase 3
        return observations.entrySet().stream()
                .filter(entry -> now - entry.getValue().receivedAtNanos() >= timeoutNanos)
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    // PHASE 3 - Added in Phase 3
    public int trackedSessionCount() {
        return observations.size();
    }

    // PHASE 3 - Added in Phase 3
    private static String requireSessionId(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionId.isBlank()) throw new IllegalArgumentException("Session ID must not be blank");
        return sessionId;
    }

    // PHASE 3 - Added in Phase 3
    private static Duration requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "timeout");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Heartbeat timeout must be positive");
        }
        return duration;
    }

    // PHASE 3 - Added in Phase 3
    private record Observation(
            // PHASE 3 - Added in Phase 3
            long sequence,
            // PHASE 3 - Added in Phase 3
            long receivedAtNanos) {
    }
}
