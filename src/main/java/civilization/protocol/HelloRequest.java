package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record HelloRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String username) implements RequestMessage {

    // PHASE 3 - Added in Phase 3
    public HelloRequest {
        ProtocolValidation.metadata(metadata);
        username = ProtocolValidation.text(username, "username", 64);
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public MessageType type() {
        return MessageType.HELLO_REQUEST;
    }
}
