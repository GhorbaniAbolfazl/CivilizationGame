package civilization.protocol;

import java.time.Instant;

// PHASE 3 - Added in Phase 3
public record ServerShutdownPush(MessageMetadata metadata, String reason,
                                 Instant shutdownAt) implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public ServerShutdownPush {
        ProtocolValidation.metadata(metadata);
        reason = ProtocolValidation.text(reason, "reason", 512);
        if (shutdownAt == null) throw new NullPointerException("shutdownAt");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.SERVER_SHUTDOWN_PUSH; }
}
