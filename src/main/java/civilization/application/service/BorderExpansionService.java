package civilization.application.service;

import civilization.application.validation.BorderExpansionValidator;
import civilization.application.validation.ValidationSupport;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.unit.BorderExpanderUnit;
import civilization.model.unit.UnitId;
import civilization.rules.GameRules;

import java.util.Set;

public final class BorderExpansionService {
    private final VisibilityService visibilityService;
    private final BorderExpansionValidator validator;

    public BorderExpansionService(VisibilityService visibilityService) {
        this(visibilityService, new BorderExpansionValidator());
    }

    public BorderExpansionService(
            VisibilityService visibilityService, BorderExpansionValidator validator) {
        this.visibilityService = java.util.Objects.requireNonNull(
                visibilityService, "visibilityService");
        this.validator = java.util.Objects.requireNonNull(validator, "validator");
    }

    public Set<HexCoord> expand(GameState state, UnitId expanderId, HexCoord selected, GameRules rules) {
        ValidationSupport.requireAllowed(validator.validate(state, expanderId, selected, rules));
        BorderExpanderUnit expander = (BorderExpanderUnit) state.player().requireUnit(expanderId);
        Set<HexCoord> capture = validator.captureCluster(selected);
        expander.actionPoints().spend(rules.borderExpansionApCost());
        state.player().border().claimAll(capture, state.map());
        state.player().removeUnit(expanderId);
        visibilityService.refresh(state, rules);
        state.incrementVersion();
        return Set.copyOf(capture);
    }
}
