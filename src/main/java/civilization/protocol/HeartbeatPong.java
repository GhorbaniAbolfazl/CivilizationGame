package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record HeartbeatPong(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String sessionId,
        // PHASE 3 - Added in Phase 3
        long sequence) implements HeartbeatMessage {

    // PHASE 3 - Added in Phase 3
    public HeartbeatPong {
        ProtocolValidation.metadata(metadata);
        sessionId = ProtocolValidation.id(sessionId, "sessionId");
        if (sequence < 0) {
            throw new IllegalArgumentException("Heartbeat sequence must not be negative");
        }
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public MessageType type() {
        return MessageType.HEARTBEAT_PONG;
    }
}
