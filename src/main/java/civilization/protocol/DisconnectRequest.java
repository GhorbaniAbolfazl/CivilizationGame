package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record DisconnectRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String reason) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public DisconnectRequest {
        ProtocolValidation.metadata(metadata);
        reason = ProtocolValidation.optionalText(reason, "reason", 256);
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.DISCONNECT_REQUEST; }
}
