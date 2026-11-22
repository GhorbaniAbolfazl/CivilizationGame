package civilization.application.validation;

import civilization.application.action.ActionPreview;
import civilization.model.building.BuildingId;
import civilization.model.building.WorkerStationBuilding;
import civilization.model.game.GameState;
import civilization.model.unit.UnitId;
import civilization.model.unit.WorkerUnit;
import civilization.rules.GameRules;

import java.util.Objects;

/** Authoritative Worker-station validation used by both preview and execution. */
public final class WorkerAssignmentValidator {
    public ActionPreview validateStation(
            GameState state, UnitId workerId, BuildingId buildingId, GameRules rules) {
        try {
            Objects.requireNonNull(workerId, "workerId");
            Objects.requireNonNull(buildingId, "buildingId");
            Objects.requireNonNull(rules, "rules");
            ActionPreview phase = ValidationSupport.playerActionPhase(state);
            if (!phase.allowed()) {
                return phase;
            }
            var unit = state.player().requireUnit(workerId);
            var building = state.player().requireBuilding(buildingId);
            if (!(unit instanceof WorkerUnit worker) || !worker.isActive()) {
                return ActionPreview.reject("Unit is not an active Worker");
            }
            if (!(building instanceof WorkerStationBuilding production) || !building.isActive()) {
                return ActionPreview.reject("Worker requires an active production building");
            }
            if (!worker.position().equals(building.position())) {
                return ActionPreview.reject("Worker must be on the building hex");
            }
            if (worker.isStationed()) {
                return ActionPreview.reject("Worker is already stationed");
            }
            if (state.player().stationedWorkerCount(buildingId) >= production.workerCapacity()) {
                return ActionPreview.reject("Building Worker capacity reached");
            }
            return worker.actionPoints().canSpend(rules.workerStationApCost())
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Insufficient AP to station Worker");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }
}
