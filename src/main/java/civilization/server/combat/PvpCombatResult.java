package civilization.server.combat;

import civilization.model.combat.CombatMode;
import civilization.model.combat.DamageRecord;
import civilization.model.combat.DiceResolution;
import civilization.model.unit.UnitId;

import java.util.List;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record PvpCombatResult(CombatMode mode, List<UnitId> participatingAttackers,
                              List<UnitId> participatingDefenders, DiceResolution dice,
                              List<DamageRecord> damage, List<UnitId> apSpentBy,
                              PvpBattleReport report) {
    // PHASE 3 - Added in Phase 3
    public PvpCombatResult {
        Objects.requireNonNull(mode, "mode");
        participatingAttackers = List.copyOf(participatingAttackers);
        participatingDefenders = List.copyOf(participatingDefenders);
        Objects.requireNonNull(dice, "dice");
        damage = List.copyOf(damage);
        apSpentBy = List.copyOf(apSpentBy);
        Objects.requireNonNull(report, "report");
    }
}
