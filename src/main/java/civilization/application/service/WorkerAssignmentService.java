package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.application.validation.ValidationSupport;
import civilization.application.validation.WorkerAssignmentValidator;
import civilization.model.building.BuildingId;
import civilization.model.game.GameState;
import civilization.model.unit.UnitId;
import civilization.model.unit.WorkerUnit;
import civilization.rules.GameRules;

import java.util.Objects;

public final class WorkerAssignmentService {
    private final WorkerAssignmentValidator validator;

    public WorkerAssignmentService() {
        this(new WorkerAssignmentValidator());
    }

    public WorkerAssignmentService(WorkerAssignmentValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public void station(GameState state, UnitId workerId, BuildingId buildingId, GameRules rules) {
        ValidationSupport.requireAllowed(validator.validateStation(state, workerId, buildingId, rules));
        WorkerUnit worker = requireWorker(state, workerId);
        int cost = rules.workerStationApCost();
        worker.actionPoints().spend(cost);
        worker.stationAt(buildingId);
        state.incrementVersion();
    }

    /** Dedicated page-5 note wins: leaving does not refund AP. */
    public void unstation(GameState state, UnitId workerId) {
        ValidationSupport.requireAllowed(ValidationSupport.playerActionPhase(state));
        WorkerUnit worker = requireWorker(state, workerId);
        if (!worker.isStationed()) {
            throw new ActionFailure("Worker is not stationed");
        }
        worker.leaveStation();
        state.incrementVersion();
    }

    private static WorkerUnit requireWorker(GameState state, UnitId workerId) {
        if (!(state.player().requireUnit(workerId) instanceof WorkerUnit worker) || !worker.isActive()) {
            throw new ActionFailure("Unit is not an active Worker");
        }
        return worker;
    }
}
