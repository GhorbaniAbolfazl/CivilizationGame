package civilization.server.trade;

import civilization.model.economy.ResourceBundle;
import civilization.server.session.PlayerId;
import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class PlayerTradeOffer {
    // PHASE 3 - Added in Phase 3
    private final String offerId;
    // PHASE 3 - Added in Phase 3
    private final PlayerId senderPlayerId;
    // PHASE 3 - Added in Phase 3
    private final PlayerId recipientPlayerId;
    // PHASE 3 - Added in Phase 3
    private final ResourceBundle offered;
    // PHASE 3 - Added in Phase 3
    private final ResourceBundle requested;
    // PHASE 3 - Added in Phase 3
    private final Instant createdAt;
    // PHASE 3 - Added in Phase 3
    private PlayerTradeStatus status = PlayerTradeStatus.PENDING;

    // PHASE 3 - Added in Phase 3
    public PlayerTradeOffer(String offerId, PlayerId senderPlayerId, PlayerId recipientPlayerId,
                            ResourceBundle offered, ResourceBundle requested, Instant createdAt) {
        this.offerId = Objects.requireNonNull(offerId, "offerId");
        this.senderPlayerId = Objects.requireNonNull(senderPlayerId, "senderPlayerId");
        this.recipientPlayerId = Objects.requireNonNull(recipientPlayerId, "recipientPlayerId");
        this.offered = Objects.requireNonNull(offered, "offered");
        this.requested = Objects.requireNonNull(requested, "requested");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    // PHASE 3 - Added in Phase 3
    public String offerId() { return offerId; }
    // PHASE 3 - Added in Phase 3
    public PlayerId senderPlayerId() { return senderPlayerId; }
    // PHASE 3 - Added in Phase 3
    public PlayerId recipientPlayerId() { return recipientPlayerId; }
    // PHASE 3 - Added in Phase 3
    public ResourceBundle offered() { return offered; }
    // PHASE 3 - Added in Phase 3
    public ResourceBundle requested() { return requested; }
    // PHASE 3 - Added in Phase 3
    public Instant createdAt() { return createdAt; }
    // PHASE 3 - Added in Phase 3
    public PlayerTradeStatus status() { return status; }

    // PHASE 3 - Added in Phase 3
    public void close(PlayerTradeStatus replacement) {
        Objects.requireNonNull(replacement, "replacement");
        if (status != PlayerTradeStatus.PENDING) {
            throw new IllegalStateException("Trade offer is already closed");
        }
        if (replacement == PlayerTradeStatus.PENDING) {
            throw new IllegalArgumentException("Closed status is required");
        }
        status = replacement;
    }

    // PHASE 3 - Added in Phase 3
    PlayerTradeOffer snapshot() {
        PlayerTradeOffer copy = new PlayerTradeOffer(offerId, senderPlayerId,
                recipientPlayerId, offered, requested, createdAt);
        if (status != PlayerTradeStatus.PENDING) copy.close(status);
        return copy;
    }
}
