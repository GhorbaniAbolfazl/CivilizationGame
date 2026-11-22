package civilization.application.event;

import java.util.Objects;
public record TurnStepCompletedEvent(int turnNumber, String stepId, int stepIndex) implements GameEvent {
    public TurnStepCompletedEvent { Objects.requireNonNull(stepId, "stepId"); }
}
