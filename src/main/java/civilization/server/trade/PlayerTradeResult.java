package civilization.server.trade;

import civilization.server.session.SessionId;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record PlayerTradeResult(PlayerTradeOffer offer, SessionId senderSessionId,
                                SessionId recipientSessionId) {
    // PHASE 3 - Added in Phase 3
    public PlayerTradeResult {
        Objects.requireNonNull(offer, "offer");
        // PHASE 3 - Added in Phase 3
        offer = offer.snapshot();
        Objects.requireNonNull(senderSessionId, "senderSessionId");
        Objects.requireNonNull(recipientSessionId, "recipientSessionId");
    }
}
