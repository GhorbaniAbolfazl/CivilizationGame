package civilization.application.factory;

import civilization.application.ActionFailure;
import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.building.ProductionBuilding;
import civilization.model.building.Settlement;
import civilization.model.building.MilitaryStable;
import civilization.model.building.Bazaar;
import civilization.model.building.Dock;
import civilization.model.building.Monument;
import civilization.model.building.Apothecary;
import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;
import civilization.rules.GameRules;

import java.util.Objects;

/** Central creation point for player-constructed Phase 1 buildings. */
public final class BuildingFactory {
    public Building create(BuildingType type, HexCoord position, GameRules rules) {
        return create(type, position, rules, FactionId.PLAYER);
    }

    public Building create(
            BuildingType type, HexCoord position, GameRules rules, FactionId ownerId) {
        return create(BuildingId.random(), type, position, rules, ownerId);
    }

    public Building create(BuildingId id, BuildingType type, HexCoord position,
            GameRules rules, FactionId ownerId) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(ownerId, "ownerId");
        var definition = rules.building(type);
        if (type == BuildingType.DOCK) {
            return new Dock(id, position, ownerId,
                    definition.maximumHealth(), definition.workerCapacity());
        }
        if (type.isProductionBuilding()) {
            return new ProductionBuilding(
                    id, type, position, definition.workerCapacity(), ownerId,
                    definition.maximumHealth());
        }
        if (type == BuildingType.SETTLEMENT) {
            return new Settlement(
                    id, position, definition.unitCapContribution(), ownerId,
                    definition.maximumHealth());
        }
        if (type == BuildingType.MILITARY_STABLE) {
            return new MilitaryStable(
                    id, position, ownerId, definition.maximumHealth());
        }
        if (type == BuildingType.BAZAAR) {
            return new Bazaar(id, position, ownerId, definition.maximumHealth());
        }
        if (type == BuildingType.MONUMENT) {
            return new Monument(id, position, ownerId, definition.maximumHealth());
        }
        // PHASE 3 - Added in Phase 3
        if (type == BuildingType.APOTHECARY) {
            return new Apothecary(id, position, ownerId, definition.maximumHealth());
        }
        throw new ActionFailure("Unsupported construction type: " + type);
    }
}
