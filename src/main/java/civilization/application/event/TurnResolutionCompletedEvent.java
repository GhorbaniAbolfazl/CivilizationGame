package civilization.application.event;

import java.util.List;
import java.util.Objects;
public record TurnResolutionCompletedEvent(int turnNumber, long stateVersion, List<String> phaseTrace)
        implements GameEvent {
    public TurnResolutionCompletedEvent { phaseTrace = List.copyOf(Objects.requireNonNull(phaseTrace, "phaseTrace")); }
}
