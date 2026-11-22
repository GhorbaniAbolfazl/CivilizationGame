package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record EndTurnRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String matchId,
        // PHASE 3 - Added in Phase 3
        long observedStateVersion) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public EndTurnRequest {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.END_TURN_REQUEST; }
}
