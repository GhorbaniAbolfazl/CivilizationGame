package civilization.application.service;

import civilization.model.building.Building;
import civilization.model.building.BuildingType;
import civilization.model.building.TownHall;
import civilization.model.disaster.DisasterId;
import civilization.model.disaster.DisasterOccurrence;
import civilization.model.disaster.DisasterType;
import civilization.model.game.GameState;
import civilization.model.infrastructure.Road;
import civilization.model.infrastructure.Wall;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.world.WildAnimal;
import civilization.model.world.WildAnimalId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Deterministic authoritative effects. No animation clock or Swing type is referenced here. */
public final class DisasterResolver {
    private final StructureLifecycleService structures;

    public DisasterResolver() { this(new StructureLifecycleService()); }
    public DisasterResolver(StructureLifecycleService structures) {
        this.structures = Objects.requireNonNull(structures, "structures");
    }

    public DisasterResult resolve(GameState state, DisasterSelector.Selection selection) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(selection, "selection");
        return switch (selection.type()) {
            case EARTHQUAKE -> earthquake(state, selection.center());
            case FLOOD -> flood(state, selection.center());
            case BEAR_ATTACK -> bearAttack(state, selection.center());
        };
    }

    public DisasterResult earthquake(GameState state, HexCoord center) {
        Set<HexCoord> area = within(state, center, 2, terrain -> true);
        DisasterOccurrence occurrence = occurrence(state, DisasterType.EARTHQUAKE, center, area, 0);
        List<DisasterResult.Impact> impacts = new ArrayList<>();
        damageUnits(state, area, 10, false, impacts);
        for (Building building : List.copyOf(state.player().allBuildings())) {
            if (!area.contains(building.position())) continue;
            int requested = building instanceof TownHall
                    ? Math.min(50, Math.max(0, building.health().current() - 1)) : 30;
            int applied = building.health().damage(requested);
            boolean removed = building.health().isDepleted();
            impacts.add(impact("BUILDING", building.id().toString(), building.position(), applied, removed));
            if (removed && !(building instanceof TownHall)) structures.destroyDepletedBuilding(state, building.id());
        }
        damageWorldStructures(state, area, 30, impacts);
        damageWalls(state, area, 30, impacts);
        state.world().disasters().register(occurrence);
        return new DisasterResult(occurrence, impacts, List.of(center));
    }

    public DisasterResult flood(GameState state, HexCoord center) {
        Set<HexCoord> area = new LinkedHashSet<>();
        area.add(center);
        center.neighbors().stream().filter(state.map()::contains)
                .filter(coord -> floodable(state.map().requireTile(coord).terrain()))
                .forEach(area::add);
        DisasterOccurrence occurrence = occurrence(state, DisasterType.FLOOD, center, area, 1);
        List<DisasterResult.Impact> impacts = new ArrayList<>();
        damageUnits(state, area, 20, true, impacts);
        for (var infrastructure : List.copyOf(state.world().infrastructure().tileInfrastructure())) {
            if (infrastructure instanceof Road road && area.contains(road.position())) {
                state.world().infrastructure().remove(road.id());
                impacts.add(impact("ROAD", road.id().toString(), road.position(), 0, true));
            }
        }
        for (Building building : List.copyOf(state.player().constructedBuildings())) {
            if (!area.contains(building.position())) continue;
            if (building.type() == BuildingType.FARM) {
                building.health().damage(building.health().current());
                structures.destroyDepletedBuilding(state, building.id());
                impacts.add(impact("FARM", building.id().toString(), building.position(), 0, true));
            } else {
                int applied = building.health().damage(30);
                boolean removed = building.health().isDepleted();
                impacts.add(impact("BUILDING", building.id().toString(), building.position(), applied, removed));
                if (removed) structures.destroyDepletedBuilding(state, building.id());
            }
        }
        TownHall hall = state.player().townHall();
        if (area.contains(hall.position())) {
            int applied = hall.health().damage(30);
            boolean removed = hall.health().isDepleted();
            impacts.add(impact("TOWN_HALL", hall.id().toString(), hall.position(), applied, removed));
            if (removed) structures.destroyDepletedBuilding(state, hall.id());
        }
        damageWorldStructures(state, area, 30, impacts);
        state.world().disasters().register(occurrence);
        List<HexCoord> path = area.stream().sorted(coords()).toList();
        return new DisasterResult(occurrence, impacts, path);
    }

    public DisasterResult bearAttack(GameState state, HexCoord nest) {
        long nearbyUnits = state.player().units().stream()
                .filter(unit -> unit.position().distanceTo(nest) <= 3).count();
        int count = nearbyUnits >= 3 ? 2 : 1;
        List<DisasterResult.Impact> impacts = new ArrayList<>();
        List<HexCoord> spawnHexes = availableBearHexes(state, nest);
        int spawned = Math.min(count, spawnHexes.size());
        for (int i = 0; i < spawned; i++) {
            HexCoord spawn = spawnHexes.get(i);
            UUID uuid = UUID.nameUUIDFromBytes((state.turn().turnNumber() + ":bear:" + nest + ":" + i)
                    .getBytes(StandardCharsets.UTF_8));
            WildAnimal bear = WildAnimal.bear(new WildAnimalId(uuid), spawn, nest);
            state.world().wildAnimals().add(bear, state.map());
            impacts.add(impact("BEAR", bear.id().toString(), spawn, 120, false));
        }
        Set<HexCoord> area = within(state, nest, 3, terrain -> true);
        DisasterOccurrence occurrence = occurrence(state, DisasterType.BEAR_ATTACK, nest, area, 0);
        state.world().disasters().register(occurrence);
        return new DisasterResult(occurrence, impacts, spawnHexes.stream().limit(spawned).toList());
    }

    private void damageUnits(GameState state, Set<HexCoord> area, int amount, boolean zeroAp,
            List<DisasterResult.Impact> impacts) {
        for (var unit : List.copyOf(state.player().units())) {
            if (!area.contains(unit.position())) continue;
            if (zeroAp) unit.actionPoints().applyPenalty(unit.actionPoints().current());
            var health = unit.healthState().orElseThrow();
            int applied = health.damage(amount);
            boolean removed = health.isDepleted();
            impacts.add(impact("UNIT", unit.id().toString(), unit.position(), applied, removed));
            if (removed) state.player().removeUnit(unit.id());
        }
        for (var unit : List.copyOf(state.world().units().all())) {
            if (!area.contains(unit.position())) continue;
            if (zeroAp) unit.actionPoints().applyPenalty(unit.actionPoints().current());
            var health = unit.healthState().orElseThrow();
            int applied = health.damage(amount);
            boolean removed = health.isDepleted();
            impacts.add(impact("WORLD_UNIT", unit.id().toString(), unit.position(), applied, removed));
            if (removed) state.world().units().remove(unit.id());
        }
    }

    private void damageWorldStructures(GameState state, Set<HexCoord> area, int amount,
            List<DisasterResult.Impact> impacts) {
        for (var structure : List.copyOf(state.world().structures().all())) {
            if (!area.contains(structure.position())) continue;
            int applied = structure.health().damage(amount);
            boolean removed = structure.health().isDepleted();
            impacts.add(impact("WORLD_STRUCTURE", structure.id().toString(), structure.position(), applied, removed));
            if (removed) state.world().structures().remove(structure.id());
        }
    }

    private void damageWalls(GameState state, Set<HexCoord> area, int amount,
            List<DisasterResult.Impact> impacts) {
        for (var infrastructure : List.copyOf(state.world().infrastructure().edgeInfrastructure())) {
            if (!(infrastructure instanceof Wall wall)
                    || (!area.contains(wall.edge().first()) && !area.contains(wall.edge().second()))) continue;
            int applied = wall.health().damage(amount);
            boolean removed = wall.health().isDepleted();
            impacts.add(impact("WALL", wall.id().toString(), wall.edge().first(), applied, removed));
            if (removed) structures.destroyDepletedWall(state, wall.id());
        }
    }

    private static List<HexCoord> availableBearHexes(GameState state, HexCoord nest) {
        List<HexCoord> candidates = state.map().coordinatesWithin(nest, 3).stream()
                .filter(coord -> state.map().requireTile(coord).terrain() == TerrainType.FOREST)
                .filter(coord -> state.world().wildAnimals().at(coord).isEmpty())
                .filter(coord -> coord.distanceTo(nest) <= 3)
                .sorted(Comparator.comparingInt((HexCoord coord) -> coord.distanceTo(nest))
                        .thenComparing(coords())).toList();
        return candidates;
    }

    private static Set<HexCoord> within(GameState state, HexCoord center, int radius,
            java.util.function.Predicate<TerrainType> terrain) {
        return state.map().coordinatesWithin(center, radius).stream()
                .filter(coord -> terrain.test(state.map().requireTile(coord).terrain()))
                .sorted(coords()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean floodable(TerrainType terrain) {
        return terrain != TerrainType.SEA && terrain != TerrainType.MOUNTAIN
                && terrain != TerrainType.MOUNTAIN_RANGE;
    }

    private static DisasterOccurrence occurrence(GameState state, DisasterType type,
            HexCoord center, Set<HexCoord> area, int effectTurns) {
        String id = "turn-%d-%s-%d-%d".formatted(state.turn().turnNumber(), type, center.q(), center.r());
        return new DisasterOccurrence(new DisasterId(id), type, center, area,
                state.turn().turnNumber(), effectTurns);
    }

    private static DisasterResult.Impact impact(String kind, String id, HexCoord location,
            int amount, boolean removed) {
        return new DisasterResult.Impact(kind, id, location, amount, removed);
    }

    private static Comparator<HexCoord> coords() {
        return Comparator.comparingInt(HexCoord::q).thenComparingInt(HexCoord::r);
    }
}
