package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record StartGameRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String lobbyId) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public StartGameRequest {
        ProtocolValidation.metadata(metadata);
        lobbyId = ProtocolValidation.id(lobbyId, "lobbyId");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.START_GAME_REQUEST; }
}
