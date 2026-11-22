package civilization.application.turn;

import civilization.application.event.EventBus;
import civilization.application.event.TurnStepCompletedEvent;
import civilization.application.event.TurnStepFailedEvent;
import civilization.application.event.TurnStepStartedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Executes named turn steps in constructor order and exposes that order for tests/defense. */
public final class TurnPipeline {
    private final List<TurnStep> steps;

    public TurnPipeline(List<? extends TurnStep> steps) {
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("A turn pipeline must contain at least one step");
        }
        Set<String> ids = new HashSet<>();
        for (TurnStep step : this.steps) {
            Objects.requireNonNull(step, "step");
            if (step.id() == null || step.id().isBlank()) {
                throw new IllegalArgumentException("Turn step IDs cannot be blank");
            }
            if (!ids.add(step.id())) {
                throw new IllegalArgumentException("Duplicate turn step ID: " + step.id());
            }
        }
    }

    public List<String> execute(TurnContext context) {
        return execute(context, EventBus.noOp());
    }

    public List<String> execute(TurnContext context, EventBus eventBus) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(eventBus, "eventBus");
        List<String> trace = new ArrayList<>(steps.size());
        int turnNumber = context.state().turn().turnNumber();
        for (int index = 0; index < steps.size(); index++) {
            TurnStep step = steps.get(index);
            trace.add(step.id());
            eventBus.publish(new TurnStepStartedEvent(turnNumber, step.id(), index));
            try {
                step.execute(context);
                eventBus.publish(new TurnStepCompletedEvent(turnNumber, step.id(), index));
            } catch (RuntimeException failure) {
                eventBus.publish(new TurnStepFailedEvent(turnNumber, step.id(), index,
                        failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
                throw failure;
            }
        }
        return List.copyOf(trace);
    }

    public List<String> stepIds() {
        return steps.stream().map(TurnStep::id).toList();
    }
}
