package civilization.application.persistence;

import civilization.application.event.EventBus;
import civilization.application.event.Subscription;
import civilization.application.event.TurnResolutionCompletedEvent;
import java.util.Objects;
import java.util.Optional;

/** Post-commit observer: exactly one autosave attempt per successfully completed turn. */
public final class AutosaveCoordinator implements AutoCloseable {
    private final Subscription subscription;
    private volatile SaveResult lastResult;

    public AutosaveCoordinator(EventBus events, GameSession session) {
        Objects.requireNonNull(session, "session");
        subscription = Objects.requireNonNull(events, "events").subscribe(
                TurnResolutionCompletedEvent.class, ignored -> lastResult = session.autosave());
    }

    public Optional<SaveResult> lastResult() { return Optional.ofNullable(lastResult); }
    @Override public void close() { subscription.close(); }
}
