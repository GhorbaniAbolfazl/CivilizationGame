package civilization.application.query;

import civilization.application.action.ActionPreview;
import civilization.application.service.UpgradeService;
import civilization.application.validation.BorderExpansionValidator;
import civilization.application.validation.ConstructionValidator;
import civilization.application.validation.MovementValidator;
import civilization.application.validation.ProductionValidator;
import civilization.application.validation.WorkerAssignmentValidator;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.TechnologyId;
import civilization.model.building.HallLevel;
import civilization.model.unit.UnitId;
import civilization.model.unit.UnitType;
import civilization.rules.GameRules;

import java.util.Objects;

/** Read-only action previews delegated to the same validators used by command services. */
public final class AvailableActionsQuery {
    private final MovementValidator movement;
    private final ConstructionValidator construction;
    private final WorkerAssignmentValidator workerAssignment;
    private final BorderExpansionValidator borderExpansion;
    private final ProductionValidator production;

    public AvailableActionsQuery() {
        this(new MovementValidator(), new ConstructionValidator(),
                new WorkerAssignmentValidator(), new BorderExpansionValidator(),
                new ProductionValidator(new UpgradeService()));
    }

    public AvailableActionsQuery(
            MovementValidator movement,
            ConstructionValidator construction,
            WorkerAssignmentValidator workerAssignment,
            BorderExpansionValidator borderExpansion,
            ProductionValidator production) {
        this.movement = Objects.requireNonNull(movement, "movement");
        this.construction = Objects.requireNonNull(construction, "construction");
        this.workerAssignment = Objects.requireNonNull(workerAssignment, "workerAssignment");
        this.borderExpansion = Objects.requireNonNull(borderExpansion, "borderExpansion");
        this.production = Objects.requireNonNull(production, "production");
    }

    public ActionPreview canMove(GameState state, UnitId unitId, HexCoord target, GameRules rules) {
        return movement.validate(state, unitId, target, rules);
    }

    public ActionPreview canQueueUnit(GameState state, UnitType type, GameRules rules) {
        return production.validateUnit(state, type, rules);
    }

    public ActionPreview canQueueUpgrade(GameState state, UpgradeId id, GameRules rules) {
        return production.validateUpgrade(state, id, rules);
    }

    public ActionPreview canUpgradeHall(GameState state, HallLevel target, GameRules rules) {
        return production.validateHallUpgrade(state, target, rules);
    }

    public ActionPreview canResearchTechnology(
            GameState state, TechnologyId id, GameRules rules) {
        return production.validateTechnology(state, id, rules);
    }

    public ActionPreview canCancelHallProduction(GameState state) {
        return production.validateCancellation(state);
    }

    public ActionPreview canConstruct(
            GameState state, UnitId builderId, BuildingType type, HexCoord target, GameRules rules) {
        return construction.validate(state, builderId, type, target, rules);
    }

    public ActionPreview canStation(GameState state, UnitId workerId, BuildingId buildingId, GameRules rules) {
        return workerAssignment.validateStation(state, workerId, buildingId, rules);
    }

    public ActionPreview canExpandBorder(
            GameState state, UnitId unitId, HexCoord selected, GameRules rules) {
        return borderExpansion.validate(state, unitId, selected, rules);
    }
}
