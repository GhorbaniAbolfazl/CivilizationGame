package civilization.application.service;

import civilization.application.event.UnitMovedEvent;
import civilization.application.event.EventBus;
import civilization.application.validation.MovementValidator;
import civilization.application.validation.ValidationSupport;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.unit.Unit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;
import civilization.rules.MovementCostPolicy;
import civilization.model.map.TerrainType;

import java.util.Objects;

public final class MovementService {
    private final VisibilityService visibilityService;
    private final MovementValidator validator;
    private final EventBus eventBus;
    private final MovementCostPolicy movementPolicy;

    public MovementService(VisibilityService visibilityService) {
        this(visibilityService, new MovementValidator(), EventBus.noOp(), new MovementCostPolicy());
    }

    public MovementService(VisibilityService visibilityService, MovementValidator validator) {
        this(visibilityService, validator, EventBus.noOp(), new MovementCostPolicy());
    }

    public MovementService(VisibilityService visibilityService, MovementValidator validator, EventBus eventBus) {
        this(visibilityService, validator, eventBus, new MovementCostPolicy());
    }

    public MovementService(VisibilityService visibilityService, MovementValidator validator,
            EventBus eventBus, MovementCostPolicy movementPolicy) {
        this.visibilityService = Objects.requireNonNull(visibilityService, "visibilityService");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.movementPolicy = Objects.requireNonNull(movementPolicy, "movementPolicy");
    }

    public int movementCost(GameState state, HexCoord destination, GameRules rules) {
        return rules.movementCost(state.map().requireTile(destination).terrain());
    }

    public int movementCost(
            GameState state, UnitId unitId, HexCoord destination, GameRules rules) {
        Unit unit = state.player().requireUnit(unitId);
        return movementPolicy.cost(state, unit, destination, rules);
    }

    public int movementCostBetween(GameState state, UnitId unitId, HexCoord origin,
            HexCoord destination, GameRules rules) {
        return movementPolicy.costBetween(
                state, state.player().requireUnit(unitId), origin, destination, rules);
    }

    public UnitMovedEvent move(GameState state, UnitId unitId, HexCoord destination, GameRules rules) {
        ValidationSupport.requireAllowed(validator.validate(state, unitId, destination, rules));
        Unit unit = state.player().requireUnit(unitId);
        int cost = movementPolicy.cost(state, unit, destination, rules);
        HexCoord origin = unit.position();
        unit.actionPoints().spend(cost);
        unit.moveTo(destination);
        unit.setEmbarked(state.map().requireTile(destination).terrain() == TerrainType.SEA);
        unit.markActive();
        visibilityService.refresh(state, rules);
        state.incrementVersion();
        UnitMovedEvent event = new UnitMovedEvent(unit.id(), origin, destination, state.stateVersion());
        eventBus.publish(event);
        return event;
    }
}
