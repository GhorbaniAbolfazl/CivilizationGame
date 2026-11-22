package civilization.application.event;

import java.util.List;
import java.util.Objects;

enum NoOpEventBus implements EventBus {
    INSTANCE;
    @Override public <T extends GameEvent> Subscription subscribe(
            Class<T> eventType, GameEventListener<? super T> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");
        return () -> { };
    }
    @Override public EventDispatchResult publish(GameEvent event) {
        Objects.requireNonNull(event, "event");
        return new EventDispatchResult(0, List.of());
    }
}
