package civilization.protocol;

import java.util.Map;
import java.util.List;
import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record BattleReportPush(MessageMetadata metadata, long stateVersion, String battleId,
                               String attackerPlayerId, String defenderPlayerId, String targetId,
                               String outcome, Map<String, Integer> remainingHitPoints,
                               List<Integer> attackerRolls, List<Integer> defenderRolls,
                               List<String> lostUnitIds, int structureDamage, Instant createdAt)
        implements PushMessage {
    // PHASE 3 - Added in Phase 3
    public BattleReportPush(MessageMetadata metadata, long stateVersion, String battleId,
                            String attackerPlayerId, String defenderPlayerId, String targetId,
                            String outcome, Map<String, Integer> remainingHitPoints) {
        this(metadata, stateVersion, battleId, attackerPlayerId, defenderPlayerId, targetId,
                outcome, remainingHitPoints, List.of(), List.of(), List.of(), 0,
                metadata.sentAt());
    }
    // PHASE 3 - Added in Phase 3
    public BattleReportPush {
        ProtocolValidation.metadata(metadata);
        ProtocolValidation.nonNegative(stateVersion, "stateVersion");
        battleId = ProtocolValidation.id(battleId, "battleId");
        attackerPlayerId = ProtocolValidation.id(attackerPlayerId, "attackerPlayerId");
        defenderPlayerId = ProtocolValidation.id(defenderPlayerId, "defenderPlayerId");
        targetId = ProtocolValidation.id(targetId, "targetId");
        outcome = ProtocolValidation.text(outcome, "outcome", 64);
        remainingHitPoints = ProtocolValidation.map(remainingHitPoints, "remainingHitPoints");
        remainingHitPoints.values().forEach(value ->
                ProtocolValidation.nonNegative(value, "remainingHitPoints value"));
        // PHASE 3 - Added in Phase 3
        attackerRolls = ProtocolValidation.list(attackerRolls, "attackerRolls");
        defenderRolls = ProtocolValidation.list(defenderRolls, "defenderRolls");
        // PHASE 3 - Added in Phase 3
        attackerRolls.forEach(value -> validateRoll(value, "attackerRoll", 7));
        defenderRolls.forEach(value -> validateRoll(value, "defenderRoll", 6));
        lostUnitIds = ProtocolValidation.list(lostUnitIds, "lostUnitIds");
        lostUnitIds.forEach(value -> ProtocolValidation.id(value, "lostUnitId"));
        ProtocolValidation.nonNegative(structureDamage, "structureDamage");
        Objects.requireNonNull(createdAt, "createdAt");
    }
    // PHASE 3 - Added in Phase 3
    private static void validateRoll(Integer value, String name, int maximum) {
        if (value == null || value < 1 || value > maximum) {
            throw new IllegalArgumentException(name + " is outside its allowed range");
        }
    }
    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.BATTLE_REPORT_PUSH; }
}
