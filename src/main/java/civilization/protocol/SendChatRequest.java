package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record SendChatRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String scopeId,
        // PHASE 3 - Added in Phase 3
        String content) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public SendChatRequest {
        ProtocolValidation.metadata(metadata);
        scopeId = ProtocolValidation.id(scopeId, "scopeId");
        content = ProtocolValidation.text(content, "content", ProtocolValidation.MAX_CHAT_LENGTH);
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.SEND_CHAT_REQUEST; }
}
