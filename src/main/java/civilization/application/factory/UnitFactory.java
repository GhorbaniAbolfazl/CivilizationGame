package civilization.application.factory;

import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;
import civilization.model.unit.ActionPoints;
import civilization.model.unit.BorderExpanderUnit;
import civilization.model.unit.BuilderUnit;
import civilization.model.unit.ExplorerUnit;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.model.unit.WorkerUnit;
import civilization.model.unit.SwordsmanUnit;
import civilization.model.unit.ArcherUnit;
import civilization.model.unit.CavalryUnit;
import civilization.rules.GameRules;
import civilization.rules.UnitDefinition;

import java.util.Objects;

/** Central creation point for Phase 1 units; future ownership is added here, not in UI code. */
public final class UnitFactory {
    public Unit create(UnitType type, HexCoord position, GameRules rules) {
        return create(type, position, rules, FactionId.PLAYER);
    }

    public Unit create(
            UnitType type, HexCoord position, GameRules rules, FactionId ownerId) {
        return create(UnitId.random(), type, position, rules, ownerId);
    }

    public Unit create(UnitId id, UnitType type, HexCoord position, GameRules rules,
            FactionId ownerId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(ownerId, "ownerId");
        UnitDefinition definition = rules.unit(type);
        ActionPoints ap = new ActionPoints(definition.actionPointCapacity());
        return switch (type) {
            case EXPLORER -> new ExplorerUnit(id, position, ap, ownerId);
            case BUILDER -> new BuilderUnit(
                    id, position, ap, definition.initialBuilderCharges(), ownerId);
            case WORKER -> new WorkerUnit(id, position, ap, ownerId);
            case BORDER_EXPANDER -> new BorderExpanderUnit(id, position, ap, ownerId);
            case SWORDSMAN -> new SwordsmanUnit(id, position, ap, ownerId,
                    definition.maximumHealth(), definition.attackRange());
            case ARCHER -> new ArcherUnit(id, position, ap, ownerId,
                    definition.maximumHealth(), definition.attackRange());
            case CAVALRY -> new CavalryUnit(id, position, ap, ownerId,
                    definition.maximumHealth(), definition.attackRange());
        };
    }
}
