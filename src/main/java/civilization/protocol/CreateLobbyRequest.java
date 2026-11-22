package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record CreateLobbyRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public CreateLobbyRequest { ProtocolValidation.metadata(metadata); }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.CREATE_LOBBY_REQUEST; }
}
