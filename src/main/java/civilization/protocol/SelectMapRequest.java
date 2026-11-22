package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record SelectMapRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String lobbyId,
        // PHASE 3 - Added in Phase 3
        String mapId) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public SelectMapRequest {
        ProtocolValidation.metadata(metadata);
        lobbyId = ProtocolValidation.id(lobbyId, "lobbyId");
        mapId = ProtocolValidation.id(mapId, "mapId");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.SELECT_MAP_REQUEST; }
}
