package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record DeclareWarRequest(MessageMetadata metadata, String matchId, String targetPlayerId,
                                long observedStateVersion) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public DeclareWarRequest {
        ProtocolValidation.metadata(metadata); matchId = ProtocolValidation.id(matchId, "matchId");
        targetPlayerId = ProtocolValidation.id(targetPlayerId, "targetPlayerId");
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.DECLARE_WAR_REQUEST; }
}
