package civilization.application.service;

import civilization.application.ActionFailure;
import civilization.application.event.EventBus;
import civilization.application.event.TurnResolutionCompletedEvent;
import civilization.application.event.TurnResolutionFailedEvent;
import civilization.application.event.TurnResolutionStartedEvent;
import civilization.application.turn.ActionPointRefreshStep;
import civilization.application.turn.ResourceProductionStep;
import civilization.application.turn.StarvationStep;
import civilization.application.turn.TownHallQueueStep;
import civilization.application.turn.TurnContext;
import civilization.application.turn.TurnPipeline;
import civilization.application.turn.UpkeepStep;
import civilization.application.turn.VisibilityStep;
import civilization.application.turn.WildlifeCombatStep;
import civilization.application.turn.TribeTurnStep;
import civilization.application.turn.SeasonTurnStep;
import civilization.application.turn.DisasterCleanupStep;
import civilization.application.turn.DisasterTurnStep;
import civilization.model.economy.TurnEconomyReport;
import civilization.model.game.GamePhase;
import civilization.model.game.GameState;
import civilization.rules.GameRules;

import java.util.List;
import java.util.Objects;

public final class TurnProcessor {
    private final TurnPipeline pipeline;
    private final EventBus eventBus;

    public TurnProcessor(
            ActionPointService actionPointService,
            ResourceProductionService productionService,
            ProductionQueueService queueService,
            ResourceUpkeepService upkeepService,
            StarvationService starvationService,
            VisibilityService visibilityService) {
        this(actionPointService, productionService, queueService, upkeepService,
                starvationService, visibilityService, EventBus.noOp());
    }

    public TurnProcessor(
            ActionPointService actionPointService,
            ResourceProductionService productionService,
            ProductionQueueService queueService,
            ResourceUpkeepService upkeepService,
            StarvationService starvationService,
            VisibilityService visibilityService,
            EventBus eventBus) {
        this(new TurnPipeline(List.of(
                new SeasonTurnStep(),
                new DisasterCleanupStep(),
                new DisasterTurnStep(eventBus),
                new ActionPointRefreshStep(actionPointService),
                new ResourceProductionStep(productionService),
                new TownHallQueueStep(queueService),
                new UpkeepStep(upkeepService),
                new StarvationStep(starvationService),
                new VisibilityStep(visibilityService),
                new WildlifeCombatStep(new CombatService()),
                new TribeTurnStep(new TribeTurnService(eventBus)))), eventBus);
    }

    public TurnProcessor(TurnPipeline pipeline) {
        this(pipeline, EventBus.noOp());
    }

    public TurnProcessor(TurnPipeline pipeline, EventBus eventBus) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public TurnResolution endTurn(GameState state, GameRules rules) {
        if (state.turn().phase() != GamePhase.PLAYER_ACTIONS) {
            throw new ActionFailure("Turn is already resolving");
        }
        state.turn().beginNextTurnResolution();
        int turnNumber = state.turn().turnNumber();
        eventBus.publish(new TurnResolutionStartedEvent(turnNumber));
        try {
            TurnContext context = new TurnContext(state, rules);
            List<String> trace = pipeline.execute(context, eventBus);
            TurnEconomyReport report = context.economyReport();
            state.turn().finishResolution(report);
            state.validateSpatialReferences();
            state.incrementVersion();
            eventBus.publish(new TurnResolutionCompletedEvent(
                    turnNumber, state.stateVersion(), trace));
            return new TurnResolution(report, trace, context.completedUnit().orElse(null));
        } catch (RuntimeException failure) {
            eventBus.publish(new TurnResolutionFailedEvent(turnNumber,
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
            throw failure;
        }
    }
}
