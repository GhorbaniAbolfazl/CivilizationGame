package civilization.application.event;

import java.util.Objects;
public record TurnResolutionFailedEvent(int turnNumber, String message) implements GameEvent {
    public TurnResolutionFailedEvent { Objects.requireNonNull(message, "message"); }
}
