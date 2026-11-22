package civilization.application.service;

import civilization.model.disaster.DisasterType;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Performs exactly one 5% roll, then uniformly chooses one eligible type/center pair. */
public final class DisasterSelector {
    public static final double PROBABILITY = 0.05;
    private final DisasterEligibilityPolicy eligibility;

    public DisasterSelector() { this(new DisasterEligibilityPolicy()); }
    public DisasterSelector(DisasterEligibilityPolicy eligibility) {
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
    }

    public Optional<Selection> select(GameState state) {
        Objects.requireNonNull(state, "state");
        if (state.gameplayRandom().nextDouble() >= PROBABILITY) return Optional.empty();
        List<Selection> candidates = new ArrayList<>();
        for (DisasterType type : DisasterType.values()) {
            eligibility.eligibleCenters(state, type).stream()
                    .forEach(center -> candidates.add(new Selection(type, center)));
        }
        candidates.sort(Comparator.comparing((Selection item) -> item.type().ordinal())
                .thenComparingInt(item -> item.center().q())
                .thenComparingInt(item -> item.center().r()));
        if (candidates.isEmpty()) return Optional.empty();
        return Optional.of(candidates.get(state.gameplayRandom().nextInt(candidates.size())));
    }

    public record Selection(DisasterType type, HexCoord center) {
        public Selection { Objects.requireNonNull(type); Objects.requireNonNull(center); }
    }
}
