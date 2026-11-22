package civilization.application.service;

import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.building.TownHall;
import civilization.model.game.GameState;
import civilization.model.infrastructure.InfrastructureId;
import civilization.model.infrastructure.Wall;
import java.util.Objects;

/** Single cleanup path for destroyed/demolished player structures and infrastructure. */
public final class StructureLifecycleService {
    private final WorkerRelocationService workerRelocation;

    public StructureLifecycleService() { this(new WorkerRelocationService()); }

    public StructureLifecycleService(WorkerRelocationService workerRelocation) {
        this.workerRelocation = Objects.requireNonNull(workerRelocation, "workerRelocation");
    }

    public void destroyDepletedBuilding(GameState state, BuildingId buildingId) {
        Building building = state.player().requireBuilding(buildingId);
        if (!building.health().isDepleted()) {
            throw new IllegalStateException("Building still has HP: " + buildingId);
        }
        cancelQueueWithoutRefund(building);
        workerRelocation.release(state, buildingId);
        state.player().destroyBuilding(buildingId);
        // PHASE 3 - Added in Phase 3
        if (building instanceof TownHall && state.player().activeHallTowns().isEmpty()) {
            state.defeat("Town Hall destroyed");
        }
    }

    public Building demolishBuilding(GameState state, BuildingId buildingId) {
        Building building = state.player().requireBuilding(buildingId);
        if (building instanceof TownHall) {
            throw new IllegalArgumentException("Town Hall cannot be voluntarily demolished");
        }
        cancelQueueWithoutRefund(building);
        workerRelocation.release(state, buildingId);
        return state.player().demolishConstructedBuilding(buildingId);
    }

    public void destroyDepletedWall(GameState state, InfrastructureId id) {
        var infrastructure = state.world().infrastructure().byId(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown infrastructure: " + id));
        if (!(infrastructure instanceof Wall wall) || !wall.health().isDepleted()) {
            throw new IllegalStateException("Wall still has HP or target is not a Wall");
        }
        state.world().infrastructure().remove(id);
    }

    private static void cancelQueueWithoutRefund(Building building) {
        if (building instanceof TownHall hall) {
            hall.productionQueue().currentOrder().ifPresent(ignored ->
                    hall.productionQueue().cancelCurrent());
        }
    }
}
