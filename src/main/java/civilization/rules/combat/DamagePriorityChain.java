package civilization.rules.combat;

import civilization.model.combat.DamageRecord;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Mandatory casualty priority encoded as a small Chain of Responsibility. */
public final class DamagePriorityChain {
    private final DamageHandler first = new MilitaryTypeDamageHandler(UnitType.SWORDSMAN,
            new MilitaryTypeDamageHandler(UnitType.ARCHER,
                    new MilitaryTypeDamageHandler(UnitType.CAVALRY, null)));

    public List<DamageRecord> applyHits(int hitCount, List<MilitaryUnit> candidates,
            Consumer<UnitId> removeUnit) {
        if (hitCount < 0) throw new IllegalArgumentException("Hit count cannot be negative");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(removeUnit, "removeUnit");
        List<DamageRecord> records = new ArrayList<>();
        for (int hit = 0; hit < hitCount; hit++) {
            first.applyOneHit(candidates, removeUnit).ifPresent(records::add);
        }
        return List.copyOf(records);
    }
}
