package civilization.application.service;

import civilization.model.economy.ResourceType;
import civilization.model.game.CrisisType;
import civilization.model.game.GameState;
import civilization.rules.GameRules;

import java.util.Objects;

public final class StarvationService {
    private final ActionPointService actionPointService;

    public StarvationService(ActionPointService actionPointService) {
        this.actionPointService = Objects.requireNonNull(actionPointService, "actionPointService");
    }

    public void update(GameState state, UpkeepResult upkeep, GameRules rules) {
        boolean starving = upkeep.shortage().amountOf(ResourceType.FOOD) > 0;
        if (starving) {
            state.crises().activate(CrisisType.STARVATION);
            actionPointService.applyPenalty(state.player(), rules.starvationApPenalty());
        } else {
            state.crises().resolve(CrisisType.STARVATION);
        }
        state.crises().setShortage(upkeep.shortage());
    }
}
