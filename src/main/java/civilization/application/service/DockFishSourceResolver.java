package civilization.application.service;

import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.map.NaturalResourceType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Canonical adjacent-Fish rule shared by placement and production. */
public final class DockFishSourceResolver {
    public List<HexCoord> availableSources(
            GameState state, HexCoord dockPosition, boolean requireDiscovered) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(dockPosition, "dockPosition");
        return state.map().neighborsOf(dockPosition).stream()
                .filter(tile -> !requireDiscovered
                        || state.player().visibility().isDiscovered(tile.coordinate()))
                .filter(tile -> tile.hasAvailableNaturalResource(NaturalResourceType.FISH))
                .map(tile -> tile.coordinate())
                .sorted(Comparator.comparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                .toList();
    }
}
