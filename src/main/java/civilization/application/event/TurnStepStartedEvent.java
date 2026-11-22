package civilization.application.event;

import java.util.Objects;
public record TurnStepStartedEvent(int turnNumber, String stepId, int stepIndex) implements GameEvent {
    public TurnStepStartedEvent { Objects.requireNonNull(stepId, "stepId"); }
}
