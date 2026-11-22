package civilization.protocol;

import java.time.Instant;

// PHASE 3 - Added in Phase 3
public record TurnChangedPush(MessageMetadata metadata, String matchId, long stateVersion,
                              long turnNumber, String activePlayerId, String phase,
                              Instant turnDeadline) implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public TurnChangedPush {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
        ProtocolValidation.nonNegative(turnNumber, "turnNumber");
        activePlayerId = ProtocolValidation.id(activePlayerId, "activePlayerId");
        phase = ProtocolValidation.text(phase, "phase", 32);
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.TURN_CHANGED_PUSH; }
}
