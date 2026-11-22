package civilization.protocol;

import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record ConnectionStatusPush(MessageMetadata metadata, String playerId,
                                   ConnectionStatus status, long stateVersion)
        implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public ConnectionStatusPush {
        ProtocolValidation.metadata(metadata);
        playerId = ProtocolValidation.id(playerId, "playerId");
        Objects.requireNonNull(status, "status");
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.CONNECTION_STATUS_PUSH; }
}
