package civilization.model.combat;

import civilization.model.unit.UnitId;
import java.util.List;
import java.util.Objects;

/** Immutable, presentation-ready explanation of every committed combat mutation. */
public record CombatResult(
        AttackRequest request,
        CombatMode mode,
        List<UnitId> participatingAttackers,
        List<UnitId> participatingDefenders,
        DiceResolution dice,
        List<DamageRecord> damage,
        List<UnitId> apSpentBy,
        CombatOutcome outcome,
        boolean captured,
        List<String> log) implements java.io.Serializable {
    public CombatResult {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(mode, "mode");
        participatingAttackers = List.copyOf(participatingAttackers);
        participatingDefenders = List.copyOf(participatingDefenders);
        damage = List.copyOf(damage);
        apSpentBy = List.copyOf(apSpentBy);
        Objects.requireNonNull(outcome, "outcome");
        log = List.copyOf(log);
    }

    public static DiceResolution noDice() {
        return new DiceResolution(List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0);
    }
}
