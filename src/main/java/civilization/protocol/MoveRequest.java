package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record MoveRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String matchId,
        // PHASE 3 - Added in Phase 3
        String unitId,
        // PHASE 3 - Added in Phase 3
        HexCoordinate destination,
        // PHASE 3 - Added in Phase 3
        long observedStateVersion) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public MoveRequest {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        unitId = ProtocolValidation.id(unitId, "unitId");
        if (destination == null) throw new NullPointerException("destination");
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.MOVE_REQUEST; }
}
