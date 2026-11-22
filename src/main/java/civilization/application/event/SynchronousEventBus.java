package civilization.application.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ordered in-process observer delivery. Listener failures never roll back game state. */
public final class SynchronousEventBus implements EventBus {
    private final CopyOnWriteArrayList<Registration<?>> registrations = new CopyOnWriteArrayList<>();

    @Override
    public <T extends GameEvent> Subscription subscribe(
            Class<T> eventType, GameEventListener<? super T> listener) {
        Registration<T> registration = new Registration<>(
                Objects.requireNonNull(eventType, "eventType"),
                Objects.requireNonNull(listener, "listener"));
        registrations.add(registration);
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) registrations.remove(registration);
        };
    }

    @Override
    public EventDispatchResult publish(GameEvent event) {
        Objects.requireNonNull(event, "event");
        int invoked = 0;
        List<EventDispatchFailure> failures = null;
        for (Registration<?> registration : registrations) {
            if (!registration.eventType().isInstance(event)) continue;
            invoked++;
            try {
                registration.invoke(event);
            } catch (RuntimeException failure) {
                if (failures == null) failures = new java.util.ArrayList<>();
                failures.add(new EventDispatchFailure(event, failure));
            }
        }
        return new EventDispatchResult(invoked, failures == null ? List.of() : failures);
    }

    /** Diagnostic/test visibility for proving controller subscriptions are released. */
    public int subscriberCount() { return registrations.size(); }

    private record Registration<T extends GameEvent>(
            Class<T> eventType, GameEventListener<? super T> listener) {
        private void invoke(GameEvent event) { listener.onEvent(eventType.cast(event)); }
    }
}
