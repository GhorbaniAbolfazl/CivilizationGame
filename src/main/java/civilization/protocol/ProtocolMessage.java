package civilization.protocol;

// PHASE 3 - Added in Phase 3
public sealed interface ProtocolMessage extends Message permits RequestMessage, ResponseMessage,
        NotificationMessage, HeartbeatMessage {

    // PHASE 3 - Added in Phase 3
    MessageMetadata metadata();

    // PHASE 3 - Added in Phase 3
    MessageType type();
}
