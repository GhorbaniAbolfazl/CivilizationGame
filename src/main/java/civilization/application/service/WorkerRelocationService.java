package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.unit.Unit;
import civilization.model.unit.WorkerUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministically releases stationed Workers onto the same or nearest legal empty land. */
public final class WorkerRelocationService {
    public void release(GameState state, BuildingId buildingId) {
        List<WorkerUnit> workers = state.player().workersStationedAt(buildingId);
        if (workers.isEmpty()) return;
        Building destroyed = state.player().requireBuilding(buildingId);
        Set<WorkerUnit> released = Set.copyOf(workers);
        Set<HexCoord> occupied = new HashSet<>();
        state.player().units().stream().filter(unit -> !released.contains(unit))
                .map(Unit::position).forEach(occupied::add);
        state.world().units().all().stream().map(Unit::position).forEach(occupied::add);
        state.world().wildAnimals().all().forEach(animal -> occupied.add(animal.position()));
        state.world().structures().all().forEach(structure -> occupied.add(structure.position()));
        state.world().tradingPosts().all().forEach(post -> occupied.add(post.position()));
        state.player().allBuildings().stream().filter(building -> !building.id().equals(buildingId))
                .map(Building::position).forEach(occupied::add);

        List<HexCoord> candidates = state.map().tiles().stream()
                .map(tile -> tile.coordinate())
                .filter(coord -> state.world().territories().ownerAt(coord).isEmpty())
                .filter(coord -> {
                    TerrainType terrain = state.map().requireTile(coord).terrain();
                    return terrain != TerrainType.SEA && terrain != TerrainType.MOUNTAIN_RANGE;
                })
                .sorted(java.util.Comparator
                        .comparingInt((HexCoord coord) -> coord.distanceTo(destroyed.position()))
                        .thenComparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                .toList();
        for (WorkerUnit worker : workers) {
            HexCoord destination = candidates.stream().filter(coord -> !occupied.contains(coord))
                    .findFirst().orElseThrow(() -> new ActionFailure(
                            "No legal empty land is available for released Worker " + worker.id()));
            worker.moveTo(destination);
            worker.leaveStation();
            occupied.add(destination);
        }
    }
}
