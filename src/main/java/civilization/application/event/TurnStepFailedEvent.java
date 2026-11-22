package civilization.application.event;

import java.util.Objects;
public record TurnStepFailedEvent(int turnNumber, String stepId, int stepIndex, String message)
        implements GameEvent {
    public TurnStepFailedEvent { Objects.requireNonNull(stepId, "stepId"); Objects.requireNonNull(message, "message"); }
}
