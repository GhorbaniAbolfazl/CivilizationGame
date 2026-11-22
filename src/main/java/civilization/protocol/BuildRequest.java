package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record BuildRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String matchId,
        // PHASE 3 - Added in Phase 3
        String builderId,
        // PHASE 3 - Added in Phase 3
        String buildingType,
        // PHASE 3 - Added in Phase 3
        HexCoordinate location,
        // PHASE 3 - Added in Phase 3
        long observedStateVersion) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public BuildRequest {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        builderId = ProtocolValidation.id(builderId, "builderId");
        buildingType = ProtocolValidation.text(buildingType, "buildingType", 64);
        if (location == null) throw new NullPointerException("location");
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.BUILD_REQUEST; }
}
