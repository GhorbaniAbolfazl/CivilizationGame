package civilization.server.combat;

import civilization.server.session.PlayerId;
import civilization.server.session.SessionId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record PvpBattleReport(String battleId, PlayerId attackerPlayerId,
                              PlayerId defenderPlayerId, SessionId attackerSessionId,
                              SessionId defenderSessionId, String targetId, String outcome,
                              List<Integer> attackerRolls, List<Integer> defenderRolls,
                              List<String> lostUnitIds, int structureDamage,
                              Map<String, Integer> remainingHitPoints, Instant createdAt) {
    // PHASE 3 - Added in Phase 3
    public PvpBattleReport {
        if (battleId == null || battleId.isBlank()) throw new IllegalArgumentException("battleId is required");
        Objects.requireNonNull(attackerPlayerId, "attackerPlayerId");
        Objects.requireNonNull(defenderPlayerId, "defenderPlayerId");
        Objects.requireNonNull(attackerSessionId, "attackerSessionId");
        Objects.requireNonNull(defenderSessionId, "defenderSessionId");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        if (outcome == null || outcome.isBlank()) throw new IllegalArgumentException("outcome is required");
        attackerRolls = List.copyOf(attackerRolls);
        defenderRolls = List.copyOf(defenderRolls);
        lostUnitIds = List.copyOf(lostUnitIds);
        if (structureDamage < 0) throw new IllegalArgumentException("structureDamage cannot be negative");
        remainingHitPoints = Map.copyOf(remainingHitPoints);
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
