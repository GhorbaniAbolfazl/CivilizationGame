package civilization.application.event;

import java.util.Objects;

public record EventDispatchFailure(GameEvent event, RuntimeException cause) {
    public EventDispatchFailure {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(cause, "cause");
    }
}
