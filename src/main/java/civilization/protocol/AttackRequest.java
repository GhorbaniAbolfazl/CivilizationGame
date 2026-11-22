package civilization.protocol;

import java.util.List;

// PHASE 3 - Added in Phase 3
public record AttackRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String matchId,
        // PHASE 3 - Added in Phase 3
        List<String> attackerUnitIds,
        // PHASE 3 - Added in Phase 3
        String targetId,
        // PHASE 3 - Added in Phase 3
        long observedStateVersion) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public AttackRequest {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        attackerUnitIds = ProtocolValidation.list(attackerUnitIds, "attackerUnitIds");
        if (attackerUnitIds.isEmpty()) {
            throw new IllegalArgumentException("At least one attacker unit is required");
        }
        attackerUnitIds.forEach(id -> ProtocolValidation.id(id, "attackerUnitId"));
        targetId = ProtocolValidation.id(targetId, "targetId");
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.ATTACK_REQUEST; }
}
