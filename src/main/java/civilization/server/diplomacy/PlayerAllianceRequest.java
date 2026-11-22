package civilization.server.diplomacy;

import civilization.server.session.PlayerId;
import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class PlayerAllianceRequest {
    // PHASE 3 - Added in Phase 3
    private final String requestId;
    // PHASE 3 - Added in Phase 3
    private final PlayerId requesterPlayerId;
    // PHASE 3 - Added in Phase 3
    private final PlayerId targetPlayerId;
    // PHASE 3 - Added in Phase 3
    private final Instant createdAt;
    // PHASE 3 - Added in Phase 3
    private AllianceRequestStatus status = AllianceRequestStatus.PENDING;

    // PHASE 3 - Added in Phase 3
    public PlayerAllianceRequest(String requestId, PlayerId requesterPlayerId,
                                 PlayerId targetPlayerId, Instant createdAt) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.requesterPlayerId = Objects.requireNonNull(requesterPlayerId, "requesterPlayerId");
        this.targetPlayerId = Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // PHASE 3 - Added in Phase 3
    public String requestId() { return requestId; }
    // PHASE 3 - Added in Phase 3
    public PlayerId requesterPlayerId() { return requesterPlayerId; }
    // PHASE 3 - Added in Phase 3
    public PlayerId targetPlayerId() { return targetPlayerId; }
    // PHASE 3 - Added in Phase 3
    public Instant createdAt() { return createdAt; }
    // PHASE 3 - Added in Phase 3
    public AllianceRequestStatus status() { return status; }

    // PHASE 3 - Added in Phase 3
    public void close(AllianceRequestStatus replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (status != AllianceRequestStatus.PENDING) {
            throw new IllegalStateException("Alliance request is already closed");
        }
        if (replacement == AllianceRequestStatus.PENDING) {
            throw new IllegalArgumentException("Closed status is required");
        }
        status = replacement;
    }

    // PHASE 3 - Added in Phase 3
    PlayerAllianceRequest snapshot() {
        PlayerAllianceRequest copy = new PlayerAllianceRequest(requestId,
                requesterPlayerId, targetPlayerId, createdAt);
        if (status != AllianceRequestStatus.PENDING) copy.close(status);
        return copy;
    }
}
