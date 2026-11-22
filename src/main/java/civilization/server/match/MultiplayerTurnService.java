package civilization.server.match;

import civilization.application.event.EventBus;
import civilization.application.factory.UnitFactory;
import civilization.application.service.*;
import civilization.application.validation.ProductionValidator;
import civilization.rules.GameRules;
import civilization.server.session.PlayerId;
import civilization.server.item.MultiplayerItemService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class MultiplayerTurnService {
    // PHASE 3 - Added in Phase 3
    private final GameRules rules;
    // PHASE 3 - Added in Phase 3
    private final TurnProcessor processor;
    // PHASE 3 - Added in Phase 3
    private final MultiplayerItemService itemService;
    // PHASE 3 - Added in Phase 3
    public MultiplayerTurnService(GameRules rules) {
        this(rules, new MultiplayerItemService(rules, Clock.systemUTC()));
    }
    // PHASE 3 - Added in Phase 3
    public MultiplayerTurnService(GameRules rules, MultiplayerItemService itemService) {
        this.rules = Objects.requireNonNull(rules); UpgradeService upgrades = new UpgradeService(); UnitFactory units = new UnitFactory(); EventBus events = EventBus.noOp();
        this.itemService = Objects.requireNonNull(itemService, "itemService");
        ProductionQueueService queue = new ProductionQueueService(upgrades, new ProductionValidator(upgrades), units);
        processor = new TurnProcessor(new ActionPointService(), new ResourceProductionService(), queue,
                new ResourceUpkeepService(), new StarvationService(new ActionPointService()), new VisibilityService(), events);
    }
    // PHASE 3 - Added in Phase 3
    public MultiplayerTurnResult endTurn(MultiplayerGameState state, PlayerId playerId, long observedVersion) {
        return resolveTurn(state, playerId, observedVersion, false);
    }
    // PHASE 3 - Added in Phase 3
    public MultiplayerTurnResult endTurnForDisconnect(MultiplayerGameState state, PlayerId playerId) {
        return resolveTurn(state, playerId, state.stateVersion(), true);
    }
    // PHASE 3 - Added in Phase 3
    private MultiplayerTurnResult resolveTurn(MultiplayerGameState state, PlayerId playerId,
                                               long observedVersion, boolean disconnecting) {
        state.requireActive(playerId, observedVersion); var view = state.activePlayerView(playerId);
        var resolution = processor.endTurn(view, rules); new HappinessService().evaluate(view);
        // PHASE 3 - Added in Phase 3
        itemService.advanceCrafting(state, playerId);
        ArrayList<String> expired = new ArrayList<>(
                state.timedItemEffects().advanceAndExpire(playerId));
        expired.addAll(state.itemEffects().expireTurn(playerId,
                state.requirePlayer(playerId).empire()));
        ArrayList<String> trace = new ArrayList<>(resolution.phaseTrace());
        trace.add("APOTHECARY_QUEUE"); trace.add("HAPPINESS"); trace.add("ITEM_EXPIRATION");
        // PHASE 3 - Added in Phase 3
        if (disconnecting && !state.disconnectForTurnResolution(playerId)) throw new IllegalStateException("Active player was already disconnected");
        state.completeTurn(playerId);
        // PHASE 3 - Added in Phase 3
        state.evaluateHallSurvival(playerId);
        // PHASE 3 - Added in Phase 3
        var battleReports = state.drainBattleReports(state.activePlayerId());
        return new MultiplayerTurnResult(state.stateVersion(), state.turn().turnNumber(), state.activePlayerId(), trace, List.copyOf(expired), battleReports);
    }
}
