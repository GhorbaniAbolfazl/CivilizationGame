package civilization.model.player;

import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.building.WorkerStationBuilding;
import civilization.model.building.TownHall;
import civilization.model.map.HexCoord;
import civilization.model.map.VisibilityState;
import civilization.model.identity.FactionId;
import civilization.model.happiness.HappinessState;
import civilization.model.technology.TechnologyState;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.model.unit.WorkerUnit;
import civilization.model.unit.MilitaryUnit;
import civilization.model.unit.MilitaryStackLimits;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.io.IOException;
import java.io.ObjectInputStream;
import civilization.model.map.GameMap;
import civilization.model.item.PlayerItemInventory;

public final class PlayerState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final FactionId factionId;
    private final TownHall townHall;
    private final UnitCapacity unitCapacity;
    private final EmpireBorder border;
    private final VisibilityState visibility;
    private final TechnologyState technologies;
    private final HappinessState happiness;
    private final Map<UnitId, Unit> units = new LinkedHashMap<>();
    private final Map<BuildingId, Building> constructedBuildings = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private Map<BuildingId, TownHall> hallTowns = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private Map<BuildingId, Set<HexCoord>> hallRegions = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private PlayerItemInventory itemInventory = new PlayerItemInventory();

    public PlayerState(
            TownHall townHall,
            UnitCapacity unitCapacity,
            EmpireBorder border,
            VisibilityState visibility,
            TechnologyState technologies) {
        this(FactionId.PLAYER, townHall, unitCapacity, border, visibility, technologies,
                new HappinessState());
    }

    public PlayerState(
            FactionId factionId,
            TownHall townHall,
            UnitCapacity unitCapacity,
            EmpireBorder border,
            VisibilityState visibility,
            TechnologyState technologies) {
        this(factionId, townHall, unitCapacity, border, visibility, technologies,
                new HappinessState());
    }

    public PlayerState(
            FactionId factionId,
            TownHall townHall,
            UnitCapacity unitCapacity,
            EmpireBorder border,
            VisibilityState visibility,
            TechnologyState technologies,
            HappinessState happiness) {
        this.factionId = Objects.requireNonNull(factionId, "factionId");
        this.townHall = Objects.requireNonNull(townHall, "townHall");
        this.unitCapacity = Objects.requireNonNull(unitCapacity, "unitCapacity");
        this.border = Objects.requireNonNull(border, "border");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.technologies = Objects.requireNonNull(technologies, "technologies");
        this.happiness = Objects.requireNonNull(happiness, "happiness");
        if (!townHall.ownerId().equals(factionId)) {
            throw new IllegalArgumentException("Town Hall owner must match the player faction");
        }
        // PHASE 3 - Added in Phase 3
        hallTowns.put(townHall.id(), townHall);
        hallRegions.put(townHall.id(), Set.copyOf(border.ownedHexes()));
    }

    public FactionId factionId() {
        return factionId;
    }

    public TownHall townHall() {
        // PHASE 3 - Added in Phase 3
        if (townHall.isActive()) return townHall;
        return hallTowns.values().stream().filter(TownHall::isActive)
                .findFirst().orElse(townHall);
    }

    // PHASE 3 - Added in Phase 3
    public Collection<TownHall> hallTowns() {
        return Collections.unmodifiableCollection(hallTowns.values());
    }

    // PHASE 3 - Added in Phase 3
    public Collection<TownHall> activeHallTowns() {
        return Collections.unmodifiableList(hallTowns.values().stream()
                .filter(TownHall::isActive).toList());
    }

    // PHASE 3 - Added in Phase 3
    public void addTownHall(TownHall hall, Set<HexCoord> controlledRegion, GameMap map) {
        Objects.requireNonNull(hall, "hall");
        Set<HexCoord> region = Set.copyOf(Objects.requireNonNull(controlledRegion, "controlledRegion"));
        Objects.requireNonNull(map, "map");
        if (!hall.ownerId().equals(factionId)) throw new IllegalArgumentException("Hall Town owner mismatch");
        if (region.isEmpty() || !region.contains(hall.position())) {
            throw new IllegalArgumentException("Hall Town region must contain its position");
        }
        if (buildingAt(hall.position()).isPresent()) {
            throw new IllegalStateException("A building already occupies the Hall Town hex");
        }
        if (hallTowns.putIfAbsent(hall.id(), hall) != null
                || constructedBuildings.containsKey(hall.id())) {
            throw new IllegalArgumentException("Duplicate building ID: " + hall.id());
        }
        // PHASE 3 - Added in Phase 3
        try {
            border.claimAll(region, map);
            hallRegions.put(hall.id(), region);
        } catch (RuntimeException failure) {
            hallTowns.remove(hall.id());
            throw failure;
        }
    }

    public UnitCapacity unitCapacity() {
        return unitCapacity;
    }

    public EmpireBorder border() {
        return border;
    }

    public VisibilityState visibility() {
        return visibility;
    }

    public TechnologyState technologies() {
        return technologies;
    }

    public HappinessState happiness() { return happiness; }

    // PHASE 3 - Added in Phase 3
    public PlayerItemInventory itemInventory() { return itemInventory; }

    public void addUnit(Unit unit) {
        Objects.requireNonNull(unit, "unit");
        if (!unit.isActive()) {
            throw new IllegalArgumentException("Cannot add a removed unit");
        }
        if (!unit.ownerId().equals(factionId)) {
            throw new IllegalArgumentException("Unit owner must match the player faction");
        }
        if (units.containsKey(unit.id())) {
            throw new IllegalArgumentException("Duplicate unit ID: " + unit.id());
        }
        if (!unitCapacity.allows(Math.toIntExact(activeUnitCount()), constructedBuildings.values())) {
            throw new IllegalStateException("Unit Cap reached");
        }
        if (unit instanceof MilitaryUnit && activeMilitaryUnitCount() >= militaryUnitCapacity()) {
            throw new IllegalStateException("Military Unit Cap reached");
        }
        if (unit instanceof MilitaryUnit && !MilitaryStackLimits.allows(units.values().stream()
                .filter(other -> other.position().equals(unit.position())).toList(), unit.type())) {
            throw new IllegalStateException("Military stacking limit reached on " + unit.position());
        }
        units.put(unit.id(), unit);
        // PHASE 3 - Added in Phase 3
        if (unit instanceof MilitaryUnit && activeMilitaryUnitCount() == militaryUnitCapacity()) {
            happiness.applyOnce("military-cap:" + townHall().level().name(), -1);
        }
    }

    public Unit requireUnit(UnitId unitId) {
        Unit unit = units.get(Objects.requireNonNull(unitId, "unitId"));
        if (unit == null) {
            throw new IllegalArgumentException("Unknown unit: " + unitId);
        }
        return unit;
    }

    public void removeUnit(UnitId unitId) {
        Unit unit = requireUnit(unitId);
        if (unit instanceof WorkerUnit worker && worker.isStationed()) {
            worker.leaveStation();
        }
        unit.remove();
        units.remove(unitId);
    }

    /** Adds an already-created unit during the production phase. */
    public void addCompletedUnit(Unit unit) {
        addUnit(unit);
    }

    public void addBuilding(Building building) {
        Objects.requireNonNull(building, "building");
        if (building instanceof TownHall) {
            throw new IllegalArgumentException("Town Hall is stored separately");
        }
        if (!building.ownerId().equals(factionId)) {
            throw new IllegalArgumentException("Building owner must match the player faction");
        }
        if (constructedBuildings.containsKey(building.id())) {
            throw new IllegalArgumentException("Duplicate building ID: " + building.id());
        }
        if (buildingAt(building.position()).isPresent()) {
            throw new IllegalStateException("A building already occupies " + building.position());
        }
        constructedBuildings.put(building.id(), building);
    }

    public void replaceBuilding(Building building) {
        Objects.requireNonNull(building, "building");
        if (building instanceof TownHall) {
            throw new IllegalArgumentException("Town Hall cannot be replaced through construction");
        }
        if (!building.ownerId().equals(factionId)) {
            throw new IllegalArgumentException("Building owner must match the player faction");
        }
        Optional<Building> existing = buildingAt(building.position());
        if (existing.isPresent()) {
            Building old = existing.orElseThrow();
            if (old instanceof TownHall || old.isActive()) {
                throw new IllegalStateException("Only a broken constructed building can be replaced");
            }
            constructedBuildings.remove(old.id());
        }
        if (constructedBuildings.containsKey(building.id())) {
            throw new IllegalArgumentException("Duplicate building ID: " + building.id());
        }
        constructedBuildings.put(building.id(), building);
    }

    public Building requireBuilding(BuildingId buildingId) {
        Objects.requireNonNull(buildingId, "buildingId");
        // PHASE 3 - Added in Phase 3
        TownHall hall = hallTowns.get(buildingId);
        if (hall != null) return hall;
        Building building = constructedBuildings.get(buildingId);
        if (building == null) {
            throw new IllegalArgumentException("Unknown building: " + buildingId);
        }
        return building;
    }

    public Optional<Building> buildingAt(HexCoord position) {
        Objects.requireNonNull(position, "position");
        // PHASE 3 - Added in Phase 3
        Optional<Building> hall = hallTowns.values().stream()
                .filter(value -> value.position().equals(position)).map(Building.class::cast)
                .findFirst();
        if (hall.isPresent()) return hall;
        return constructedBuildings.values().stream()
                .filter(building -> building.position().equals(position))
                .findFirst();
    }

    public long activeUnitCount() {
        return units.values().stream().filter(Unit::isActive).count();
    }

    public long activeUnitCount(UnitType type) {
        return units.values().stream().filter(Unit::isActive).filter(unit -> unit.type() == type).count();
    }

    public long activeMilitaryUnitCount() {
        return units.values().stream().filter(Unit::isActive)
                .filter(MilitaryUnit.class::isInstance).count();
    }

    public int militaryUnitCapacity() {
        // PHASE 3 - Added in Phase 3
        return townHall().level().militaryUnitCap();
    }

    public int stationedWorkerCount(BuildingId buildingId) {
        Building building = requireBuilding(buildingId);
        if (!(building instanceof WorkerStationBuilding productionBuilding)) {
            return 0;
        }
        long count = units.values().stream()
                .filter(WorkerUnit.class::isInstance)
                .map(WorkerUnit.class::cast)
                .filter(Unit::isActive)
                .filter(worker -> worker.stationedAt().filter(buildingId::equals).isPresent())
                .count();
        if (count > productionBuilding.workerCapacity()) {
            throw new IllegalStateException("Stored Worker assignments exceed building capacity");
        }
        return Math.toIntExact(count);
    }

    /** One-pass assignment index for production and snapshot calculations. */
    public Map<BuildingId, Integer> stationedWorkerCounts() {
        Map<BuildingId, Integer> counts = new LinkedHashMap<>();
        for (Unit unit : units.values()) {
            if (!(unit instanceof WorkerUnit worker) || !worker.isActive()
                    || worker.stationedAt().isEmpty()) continue;
            BuildingId buildingId = worker.stationedAt().orElseThrow();
            Building building = requireBuilding(buildingId);
            if (!(building instanceof WorkerStationBuilding station)) {
                throw new IllegalStateException("Worker references a non-station building");
            }
            int count = counts.merge(buildingId, 1, Math::addExact);
            if (count > station.workerCapacity()) {
                throw new IllegalStateException("Stored Worker assignments exceed building capacity");
            }
        }
        return Map.copyOf(counts);
    }

    /** Releases every Worker assigned to a building and leaves each Worker idle on its current hex. */
    public void releaseWorkersFromBuilding(BuildingId buildingId) {
        Objects.requireNonNull(buildingId, "buildingId");
        units.values().stream()
                .filter(WorkerUnit.class::isInstance)
                .map(WorkerUnit.class::cast)
                .filter(Unit::isActive)
                .filter(worker -> worker.stationedAt().filter(buildingId::equals).isPresent())
                .forEach(WorkerUnit::leaveStation);
    }

    public java.util.List<WorkerUnit> workersStationedAt(BuildingId buildingId) {
        Objects.requireNonNull(buildingId, "buildingId");
        return units.values().stream()
                .filter(WorkerUnit.class::isInstance)
                .map(WorkerUnit.class::cast)
                .filter(Unit::isActive)
                .filter(worker -> worker.stationedAt().filter(buildingId::equals).isPresent())
                .sorted(java.util.Comparator.comparing(worker -> worker.id().value()))
                .toList();
    }

    /** Applies structure lifecycle cleanup after combat or a disaster. */
    public void destroyBuilding(BuildingId buildingId) {
        Building building = requireBuilding(buildingId);
        if (!building.health().isDepleted()) {
            throw new IllegalStateException("Building still has HP: " + buildingId);
        }
        building.destroy();
        // PHASE 3 - Added in Phase 3
        if (building instanceof TownHall hall) {
            releaseHallRegion(hall.id());
        } else {
            releaseWorkersFromBuilding(buildingId);
            constructedBuildings.remove(buildingId);
        }
    }

    /** Removes an eligible owner-demolished building without requiring prior combat damage. */
    public Building demolishConstructedBuilding(BuildingId buildingId) {
        Building building = requireBuilding(buildingId);
        if (building instanceof TownHall) {
            throw new IllegalArgumentException("Town Hall cannot be voluntarily demolished");
        }
        releaseWorkersFromBuilding(buildingId);
        building.demolish();
        constructedBuildings.remove(buildingId);
        return building;
    }

    public Collection<Unit> units() {
        return Collections.unmodifiableList(units.values().stream().filter(Unit::isActive).toList());
    }

    public Collection<Building> constructedBuildings() {
        return Collections.unmodifiableCollection(constructedBuildings.values());
    }

    public Collection<Building> allBuildings() {
        java.util.List<Building> all = new java.util.ArrayList<>();
        // PHASE 3 - Added in Phase 3
        all.addAll(hallTowns.values());
        all.addAll(constructedBuildings.values());
        return Collections.unmodifiableList(all);
    }

    // PHASE 3 - Added in Phase 3
    public Set<HexCoord> hallRegion(BuildingId hallId) {
        Set<HexCoord> region = hallRegions.get(Objects.requireNonNull(hallId, "hallId"));
        if (region == null) throw new IllegalArgumentException("Unknown Hall Town region");
        return Collections.unmodifiableSet(region);
    }

    // PHASE 3 - Added in Phase 3
    public void eliminateRemainingAssets() {
        List.copyOf(units.values()).forEach(unit -> removeUnit(unit.id()));
        // PHASE 3 - Added in Phase 3
        List.copyOf(constructedBuildings.values()).forEach(building -> {
            releaseWorkersFromBuilding(building.id());
            building.demolish();
            constructedBuildings.remove(building.id());
        });
        border.clear();
    }

    // PHASE 3 - Added in Phase 3
    private void releaseHallRegion(BuildingId destroyedHallId) {
        Set<HexCoord> region = hallRegions.getOrDefault(destroyedHallId, Set.of());
        java.util.HashSet<HexCoord> stillControlled = new java.util.HashSet<>();
        // PHASE 3 - Added in Phase 3
        hallTowns.values().stream().filter(TownHall::isActive)
                .filter(hall -> !hall.id().equals(destroyedHallId))
                .forEach(hall -> stillControlled.addAll(
                        hallRegions.getOrDefault(hall.id(), Set.of())));
        java.util.HashSet<HexCoord> released = new java.util.HashSet<>(region);
        released.removeAll(stillControlled);
        border.releaseAll(released);
    }

    // PHASE 3 - Added in Phase 3
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        // PHASE 3 - Added in Phase 3
        if (hallTowns == null) hallTowns = new LinkedHashMap<>();
        hallTowns.putIfAbsent(townHall.id(), townHall);
        if (hallRegions == null) hallRegions = new LinkedHashMap<>();
        hallRegions.putIfAbsent(townHall.id(), Set.copyOf(border.ownedHexes()));
        // PHASE 3 - Added in Phase 3
        if (itemInventory == null) itemInventory = new PlayerItemInventory();
    }

    public int totalUnitCapacity() {
        return unitCapacity.totalCapacity(constructedBuildings.values());
    }
}
