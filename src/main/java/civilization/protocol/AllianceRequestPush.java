package civilization.protocol;

import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record AllianceRequestPush(MessageMetadata metadata, String matchId, long stateVersion,
                                  String requestId, String requesterPlayerId,
                                  String targetPlayerId, String status, Instant createdAt)
        implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public AllianceRequestPush {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
        requestId = ProtocolValidation.id(requestId, "requestId");
        requesterPlayerId = ProtocolValidation.id(requesterPlayerId, "requesterPlayerId");
        targetPlayerId = ProtocolValidation.id(targetPlayerId, "targetPlayerId");
        status = ProtocolValidation.text(status, "status", 32);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.ALLIANCE_REQUEST_PUSH; }
}
