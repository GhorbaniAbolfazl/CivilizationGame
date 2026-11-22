package civilization.rules.combat;

import civilization.model.combat.DamageRecord;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.UnitId;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

interface DamageHandler {
    Optional<DamageRecord> applyOneHit(List<MilitaryUnit> candidates, Consumer<UnitId> removeUnit);
}
