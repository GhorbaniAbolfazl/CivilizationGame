package civilization.application.service;

import civilization.model.disaster.DisasterOccurrence;
import civilization.model.map.HexCoord;
import java.util.List;
import java.util.Objects;

/** Immutable output from deterministic disaster simulation, suitable for tests and animation. */
public record DisasterResult(DisasterOccurrence occurrence, List<Impact> impacts,
        List<HexCoord> animationPath) {
    public DisasterResult {
        Objects.requireNonNull(occurrence, "occurrence");
        impacts = List.copyOf(Objects.requireNonNull(impacts, "impacts"));
        animationPath = List.copyOf(Objects.requireNonNull(animationPath, "animationPath"));
    }

    public record Impact(String kind, String targetId, HexCoord location, int amount,
            boolean removed) {
        public Impact {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(location, "location");
            if (amount < 0) throw new IllegalArgumentException("Impact amount cannot be negative");
        }
    }
}
