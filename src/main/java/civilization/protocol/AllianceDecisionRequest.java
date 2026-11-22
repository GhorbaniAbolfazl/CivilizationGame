package civilization.protocol;

import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record AllianceDecisionRequest(MessageMetadata metadata, String matchId, String requestId,
                                      AllianceDecision decision, long observedStateVersion)
        implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public AllianceDecisionRequest {
        ProtocolValidation.metadata(metadata); matchId = ProtocolValidation.id(matchId, "matchId");
        requestId = ProtocolValidation.id(requestId, "requestId");
        Objects.requireNonNull(decision, "decision");
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.ALLIANCE_DECISION_REQUEST; }
}
