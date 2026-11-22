package civilization.model.world;

import civilization.model.identity.FactionId;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.MilitaryStackLimits;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Owner-aware registry for tribe, barbarian, and neutral units. */
public final class WorldUnitRegistry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<UnitId, Unit> units = new LinkedHashMap<>();

    public void add(Unit unit, GameMap map) {
        Objects.requireNonNull(unit, "unit");
        var tile = Objects.requireNonNull(map, "map").requireTile(unit.position());
        if (tile.terrain() == civilization.model.map.TerrainType.MOUNTAIN_RANGE) {
            throw new IllegalArgumentException("World unit cannot occupy Mountain Range");
        }
        if (unit.ownerId().equals(FactionId.PLAYER)) {
            throw new IllegalArgumentException("Player units belong in PlayerState");
        }
        if (!unit.isActive()) throw new IllegalArgumentException("Removed unit cannot be registered");
        if (unit instanceof MilitaryUnit && !MilitaryStackLimits.allows(at(unit.position()), unit.type())) {
            throw new IllegalStateException("Military stacking limit reached on " + unit.position());
        }
        if (units.putIfAbsent(unit.id(), unit) != null) {
            throw new IllegalArgumentException("Duplicate world unit ID: " + unit.id());
        }
    }

    public Unit require(UnitId id) {
        Unit unit = units.get(Objects.requireNonNull(id, "id"));
        if (unit == null) throw new IllegalArgumentException("Unknown world unit: " + id);
        return unit;
    }

    public void remove(UnitId id) {
        Unit unit = require(id);
        unit.remove();
        units.remove(id);
    }

    public Collection<Unit> at(HexCoord position) {
        Objects.requireNonNull(position, "position");
        return units.values().stream().filter(Unit::isActive)
                .filter(unit -> unit.position().equals(position)).toList();
    }

    public Collection<Unit> all() {
        return Collections.unmodifiableList(units.values().stream().filter(Unit::isActive).toList());
    }
}
