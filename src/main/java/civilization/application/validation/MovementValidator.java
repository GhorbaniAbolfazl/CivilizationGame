package civilization.application.validation;

import civilization.application.action.ActionPreview;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.unit.UnitId;
import civilization.model.unit.WorkerUnit;
import civilization.model.unit.MilitaryUnit;
import civilization.rules.GameRules;
import civilization.rules.MovementCostPolicy;
import civilization.rules.combat.MilitaryStackingPolicy;

import java.util.Objects;

/** Authoritative movement validation used by both preview and execution. */
public final class MovementValidator {
    private final MovementCostPolicy movementPolicy;
    private final MilitaryStackingPolicy stackingPolicy = new MilitaryStackingPolicy();

    public MovementValidator() { this(new MovementCostPolicy()); }

    public MovementValidator(MovementCostPolicy movementPolicy) {
        this.movementPolicy = Objects.requireNonNull(movementPolicy, "movementPolicy");
    }

    public ActionPreview validate(
            GameState state, UnitId unitId, HexCoord destination, GameRules rules) {
        try {
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(rules, "rules");
            ActionPreview phase = ValidationSupport.playerActionPhase(state);
            if (!phase.allowed()) {
                return phase;
            }
            var unit = state.player().requireUnit(unitId);
            state.map().requireTile(destination);
            if (!unit.isActive() || unit instanceof WorkerUnit worker && worker.isStationed()) {
                return ActionPreview.reject("Unit cannot move in its current state");
            }
            if (!unit.position().isAdjacentTo(destination)) {
                return ActionPreview.reject("Movement is limited to an adjacent hex");
            }
            if (unit instanceof MilitaryUnit) {
                var occupants = state.player().units().stream()
                        .filter(other -> other.position().equals(destination)).toList();
                if (!stackingPolicy.allows(occupants, unit.type())) {
                    return ActionPreview.reject("Military stacking limit reached on destination hex");
                }
            }
            var rejection = movementPolicy.rejection(state, unit, destination);
            if (rejection.isPresent()) return ActionPreview.reject(rejection.orElseThrow());
            int cost = movementPolicy.cost(state, unit, destination, rules);
            if (cost <= 0) return ActionPreview.reject("Unit has no AP remaining");
            return unit.actionPoints().canSpend(cost)
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Insufficient AP for movement");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }
}
