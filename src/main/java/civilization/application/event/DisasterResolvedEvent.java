package civilization.application.event;

import civilization.application.service.DisasterResult;
import java.util.Objects;

/** Authoritative simulation result; animation observers must never mutate it. */
public record DisasterResolvedEvent(DisasterResult result) implements GameEvent {
    public DisasterResolvedEvent { Objects.requireNonNull(result, "result"); }
}
