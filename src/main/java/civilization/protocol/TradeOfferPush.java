package civilization.protocol;

import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record TradeOfferPush(MessageMetadata metadata, String matchId, long stateVersion,
                             String offerId, String senderPlayerId, String recipientPlayerId,
                             ResourceBundleDto offered, ResourceBundleDto requested,
                             String status, Instant createdAt) implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public TradeOfferPush {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
        offerId = ProtocolValidation.id(offerId, "offerId");
        senderPlayerId = ProtocolValidation.id(senderPlayerId, "senderPlayerId");
        recipientPlayerId = ProtocolValidation.id(recipientPlayerId, "recipientPlayerId");
        Objects.requireNonNull(offered, "offered");
        Objects.requireNonNull(requested, "requested");
        status = ProtocolValidation.text(status, "status", 32);
        Objects.requireNonNull(createdAt, "createdAt");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.TRADE_OFFER_PUSH; }
}
