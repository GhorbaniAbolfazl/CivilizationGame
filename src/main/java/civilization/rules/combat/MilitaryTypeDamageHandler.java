package civilization.rules.combat;

import civilization.model.combat.DamageRecord;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

final class MilitaryTypeDamageHandler implements DamageHandler {
    private final UnitType handledType;
    private final DamageHandler next;

    MilitaryTypeDamageHandler(UnitType handledType, DamageHandler next) {
        this.handledType = Objects.requireNonNull(handledType, "handledType");
        this.next = next;
    }

    @Override
    public Optional<DamageRecord> applyOneHit(
            List<MilitaryUnit> candidates, Consumer<UnitId> removeUnit) {
        Optional<MilitaryUnit> target = candidates.stream().filter(MilitaryUnit::isActive)
                .filter(unit -> unit.type() == handledType)
                .min(Comparator.comparingInt((MilitaryUnit unit) -> unit.health().current())
                        .thenComparing(unit -> unit.id().value()));
        if (target.isEmpty()) {
            return next == null ? Optional.empty() : next.applyOneHit(candidates, removeUnit);
        }
        MilitaryUnit unit = target.orElseThrow();
        int applied = unit.health().damage(1);
        boolean removed = unit.health().isDepleted();
        if (removed) removeUnit.accept(unit.id());
        return Optional.of(new DamageRecord(unit.type().name(), unit.id().value().toString(),
                applied, unit.health().current(), removed));
    }
}
