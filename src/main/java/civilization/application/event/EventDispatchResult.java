package civilization.application.event;

import java.util.List;
import java.util.Objects;

public record EventDispatchResult(int listenersInvoked, List<EventDispatchFailure> failures) {
    public EventDispatchResult {
        if (listenersInvoked < 0) throw new IllegalArgumentException("Listener count cannot be negative");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
    }
    public boolean successful() { return failures.isEmpty(); }
}
