package civilization.rules;

import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.technology.TechnologyId;
import civilization.model.unit.Unit;
import civilization.model.infrastructure.Road;
import java.util.Optional;

/** Shared passability/cost policy used by movement preview and execution. */
public final class MovementCostPolicy {
    private final SeasonPolicy seasons = new SeasonPolicy();
    public Optional<String> rejection(GameState state, Unit unit, HexCoord destination) {
        TerrainType terrain = state.map().requireTile(destination).terrain();
        if (terrain == TerrainType.MOUNTAIN_RANGE) return Optional.of("Mountain ranges are impassable");
        if (terrain == TerrainType.SEA
                && !state.player().technologies().isResearched(TechnologyId.NAVIGATION)) {
            return Optional.of("Sea movement requires Navigation");
        }
        return Optional.empty();
    }

    public int cost(GameState state, Unit unit, HexCoord destination, GameRules rules) {
        return costBetween(state, unit, unit.position(), destination, rules);
    }

    public int costBetween(GameState state, Unit unit, HexCoord origin,
            HexCoord destination, GameRules rules) {
        TerrainType destinationTerrain = state.map().requireTile(destination).terrain();
        TerrainType originTerrain = state.map().requireTile(origin).terrain();
        if (destinationTerrain == TerrainType.SEA && originTerrain != TerrainType.SEA) {
            return unit.actionPoints().current();
        }
        boolean roadConnection = state.world().infrastructure().at(origin)
                .filter(Road.class::isInstance).isPresent()
                && state.world().infrastructure().at(destination)
                .filter(Road.class::isInstance).isPresent();
        int cost = roadConnection ? rules.roadMovementCost()
                : rules.movementCost(destinationTerrain);
        if (state.map().hasRiver(origin, destination) && !roadConnection) {
            cost = Math.addExact(cost, rules.riverCrossingApCost());
        }
        cost = Math.addExact(cost,
                seasons.movementAdjustment(state.season().season(), destinationTerrain));
        return cost;
    }
}
