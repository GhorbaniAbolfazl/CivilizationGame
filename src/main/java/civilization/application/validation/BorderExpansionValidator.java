package civilization.application.validation;

import civilization.application.action.ActionPreview;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.unit.BorderExpanderUnit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Authoritative border-expansion validation used by both preview and execution. */
public final class BorderExpansionValidator {
    public ActionPreview validate(
            GameState state, UnitId unitId, HexCoord selected, GameRules rules) {
        try {
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(selected, "selected");
            Objects.requireNonNull(rules, "rules");
            ActionPreview phase = ValidationSupport.playerActionPhase(state);
            if (!phase.allowed()) {
                return phase;
            }
            var unit = state.player().requireUnit(unitId);
            if (!(unit instanceof BorderExpanderUnit) || !unit.isActive()) {
                return ActionPreview.reject("An active Border Expander is required");
            }
            if (!unit.position().equals(selected)) {
                return ActionPreview.reject("Border Expander must stand on the selected hex");
            }
            Set<HexCoord> capture = captureCluster(selected);
            if (capture.size() != 7 || capture.stream().anyMatch(coord -> !state.map().contains(coord))) {
                return ActionPreview.reject(
                        "The complete seven-hex capture cluster must be inside the map");
            }
            if (capture.stream().anyMatch(coord ->
                    !state.player().visibility().isDiscoveredByExplorer(coord))) {
                return ActionPreview.reject(
                        "Every captured hex must have been explored by an Explorer");
            }
            if (capture.stream().anyMatch(coord ->
                    state.world().territories().ownerAt(coord).isPresent())) {
                return ActionPreview.reject(
                        "Tribe/enemy territory requires legal combat capture");
            }
            boolean connected = capture.stream().anyMatch(state.player().border()::owns)
                    || capture.stream().flatMap(coord -> coord.neighbors().stream())
                    .anyMatch(state.player().border()::owns);
            if (!connected) {
                return ActionPreview.reject("New territory must remain contiguous");
            }
            return unit.actionPoints().canSpend(rules.borderExpansionApCost())
                    ? ActionPreview.allow()
                    : ActionPreview.reject("Insufficient AP for border expansion");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ActionPreview.reject(failure.getMessage());
        }
    }

    public Set<HexCoord> captureCluster(HexCoord selected) {
        Objects.requireNonNull(selected, "selected");
        Set<HexCoord> capture = new HashSet<>(selected.neighbors());
        capture.add(selected);
        return Set.copyOf(capture);
    }
}
