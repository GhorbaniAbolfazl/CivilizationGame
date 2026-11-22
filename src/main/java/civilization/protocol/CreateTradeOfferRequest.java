package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record CreateTradeOfferRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String matchId,
        // PHASE 3 - Added in Phase 3
        String recipientPlayerId,
        // PHASE 3 - Added in Phase 3
        ResourceBundleDto offered,
        // PHASE 3 - Added in Phase 3
        ResourceBundleDto requested,
        // PHASE 3 - Added in Phase 3
        long observedStateVersion) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public CreateTradeOfferRequest {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        recipientPlayerId = ProtocolValidation.id(recipientPlayerId, "recipientPlayerId");
        if (offered == null || requested == null) {
            throw new NullPointerException("offered and requested resources are required");
        }
        if (offered.quantities().isEmpty() && requested.quantities().isEmpty()) {
            throw new IllegalArgumentException("Trade offer must contain resources");
        }
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.CREATE_TRADE_OFFER_REQUEST; }
}
