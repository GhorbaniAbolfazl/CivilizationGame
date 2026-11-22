package civilization.application.query;

import civilization.model.game.GameState;

import java.util.EnumMap;

/** Copies model state into immutable, visibility-safe data for the Swing map. */
public final class MapSnapshotFactory {
    private final TribeQuery tribeQuery = new TribeQuery();
    public MapSnapshot create(GameState state) {
        return create(state, state.player().stationedWorkerCounts());
    }

    MapSnapshot create(GameState state,
            java.util.Map<civilization.model.building.BuildingId, Integer> workerCounts) {
        var hexes = state.map().tiles().stream().map(tile -> {
            boolean discovered = state.player().visibility().isDiscovered(tile.coordinate());
            var resources = new EnumMap<civilization.model.map.NaturalResourceType, Integer>(
                    civilization.model.map.NaturalResourceType.class);
            if (discovered) {
                tile.resourceDeposits().forEach(
                        (type, deposit) -> resources.put(type, deposit.remainingAmount()));
            }
            return new HexSnapshot(
                    tile.coordinate(),
                    discovered,
                    state.player().visibility().isCurrentlyVisible(tile.coordinate()),
                    state.player().border().owns(tile.coordinate()),
                    discovered ? tile.terrain() : null,
                    resources);
        }).toList();
        var units = state.player().units().stream()
                .map(unit -> new UnitSnapshot(
                        unit.id(), unit.type(), unit.position(), unit.actionPoints().current(),
                        unit.ownerId(), unit.healthState().map(
                                civilization.model.common.Health::current).orElse(0),
                        unit.healthState().map(
                                civilization.model.common.Health::maximum).orElse(0), unit.isEmbarked()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        state.world().units().all().stream()
                .filter(unit -> state.player().visibility().isCurrentlyVisible(unit.position()))
                .map(unit -> new UnitSnapshot(unit.id(), unit.type(), unit.position(),
                        unit.actionPoints().current(), unit.ownerId(),
                        unit.healthState().map(civilization.model.common.Health::current).orElse(0),
                        unit.healthState().map(civilization.model.common.Health::maximum).orElse(0),
                        unit.isEmbarked()))
                .forEach(units::add);
        var animals = state.world().wildAnimals().all().stream()
                .filter(animal -> state.player().visibility().isCurrentlyVisible(animal.position()))
                .map(animal -> new WildAnimalSnapshot(animal.id(), animal.position(),
                        animal.health().current(), animal.health().maximum(), animal.isBear()))
                .toList();
        var buildings = state.player().allBuildings().stream().map(building -> {
            int capacity = building instanceof civilization.model.building.WorkerStationBuilding production
                    ? production.workerCapacity() : 0;
            return new BuildingSnapshot(
                    building.id(), building.type(), building.position(), building.status(),
                    workerCounts.getOrDefault(building.id(), 0), capacity);
        }).toList();
        var tradingPosts = state.world().tradingPosts().all().stream()
                .filter(post -> state.player().visibility().isDiscovered(post.position()))
                .map(post -> new TradingPostSnapshot(
                        post.id(), post.position(), state.player().border().owns(post.position()),
                        post.tradeCooldown().isAvailable(state.turn().turnNumber())))
                .toList();
        var roads = state.world().infrastructure().tileInfrastructure().stream()
                .filter(civilization.model.infrastructure.Road.class::isInstance)
                .map(civilization.model.infrastructure.Road.class::cast)
                .map(civilization.model.infrastructure.Road::position)
                .filter(state.player().visibility()::isDiscovered)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var rivers = state.map().riverEdges().stream()
                .filter(edge -> state.player().visibility().isDiscovered(edge.first())
                        && state.player().visibility().isDiscovered(edge.second()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var walls = state.world().infrastructure().edgeInfrastructure().stream()
                .filter(civilization.model.infrastructure.Wall.class::isInstance)
                .map(civilization.model.infrastructure.Wall.class::cast)
                .filter(wall -> state.player().visibility().isDiscovered(wall.edge().first())
                        && state.player().visibility().isDiscovered(wall.edge().second()))
                .map(wall -> new WallSnapshot(wall.edge(), wall.health().current(),
                        wall.health().maximum())).toList();
        return new MapSnapshot(state.stateVersion(), hexes, units, buildings, tradingPosts,
                tribeQuery.discoveredTribes(state), roads, rivers, walls, animals);
    }

}
