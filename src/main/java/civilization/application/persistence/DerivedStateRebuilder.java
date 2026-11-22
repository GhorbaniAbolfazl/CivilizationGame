package civilization.application.persistence;

import civilization.model.game.GameState;
import java.util.Objects;

/** Recomputes only non-authoritative policy objects; it performs no gameplay action. */
public final class DerivedStateRebuilder {
    public void rebuild(GameState state) {
        Objects.requireNonNull(state, "state");
        state.world().relationships().all().forEach(
                civilization.model.diplomacy.RelationshipState::rebuildDerivedBehavior);
        // Adjacency, Happiness tier, capacities, and visibility projections are pure queries.
        // Authoritative Fog sets and registries are deliberately not refreshed or mutated here.
    }
}
