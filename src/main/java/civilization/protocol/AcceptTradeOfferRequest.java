package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record AcceptTradeOfferRequest(MessageMetadata metadata, String matchId, String offerId,
                                      long observedStateVersion) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public AcceptTradeOfferRequest {
        ProtocolValidation.metadata(metadata); matchId = ProtocolValidation.id(matchId, "matchId");
        offerId = ProtocolValidation.id(offerId, "offerId");
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.ACCEPT_TRADE_OFFER_REQUEST; }
}
