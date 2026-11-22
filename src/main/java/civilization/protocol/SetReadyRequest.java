package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record SetReadyRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String lobbyId,
        // PHASE 3 - Added in Phase 3
        boolean ready) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public SetReadyRequest {
        ProtocolValidation.metadata(metadata);
        lobbyId = ProtocolValidation.id(lobbyId, "lobbyId");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.SET_READY_REQUEST; }
}
