package civilization.application.service;

import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import civilization.model.tribe.Tribe;

/** One-action relationship deltas for ordinary help and hostile world actions. */
public final class RelationEventPolicy {
    public static final int ORDINARY_TRADE_HELP = 1;
    public static final int NEARBY_RESOURCE_ENCROACHMENT = -2;
    public static final int ROUTE_BLOCKING_WALL = -3;
    public void tribeTrade(GameState state, civilization.model.identity.TribeId tribeId) {
        state.world().relationships().require(tribeId).adjustScore(ORDINARY_TRADE_HELP);
    }
    public void resourceConstruction(GameState state, HexCoord target) {
        if (state.map().requireTile(target).resourceDeposits().isEmpty()) return;
        state.world().tribes().all().stream().filter(tribe -> !tribe.isDefeated())
                .filter(tribe -> tribe.territory().stream().anyMatch(hex -> hex.distanceTo(target)<=1))
                .forEach(tribe -> adjust(state,tribe,NEARBY_RESOURCE_ENCROACHMENT));
    }
    public void wallBuilt(GameState state, HexEdge edge) {
        state.world().tribes().all().stream().filter(tribe -> !tribe.isDefeated())
                .filter(tribe -> tribe.forbiddenArea().contains(edge.first())
                        || tribe.forbiddenArea().contains(edge.second()))
                .forEach(tribe -> adjust(state,tribe,ROUTE_BLOCKING_WALL));
    }
    private static void adjust(GameState state, Tribe tribe, int delta) {
        state.world().relationships().require(tribe.id()).adjustScore(delta);
    }
}
