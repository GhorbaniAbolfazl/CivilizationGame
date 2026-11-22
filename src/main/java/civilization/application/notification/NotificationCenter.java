package civilization.application.notification;

import civilization.application.event.EventBus;
import civilization.application.event.NotificationPublishedEvent;
import civilization.application.event.Subscription;
import civilization.model.map.HexCoord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Swing-independent notification history and publication facade. */
public final class NotificationCenter implements AutoCloseable {
    private final EventBus eventBus;
    private final List<GameNotification> history = new CopyOnWriteArrayList<>();
    private final Subscription subscription;
    private final AtomicLong nextId = new AtomicLong(1);

    public NotificationCenter(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        subscription = eventBus.subscribe(NotificationPublishedEvent.class,
                event -> history.add(event.notification()));
    }

    public GameNotification publish(int turnNumber, NotificationSeverity severity,
            NotificationCategory category, String message, HexCoord location) {
        GameNotification notification = new GameNotification(
                new NotificationId(nextId.getAndIncrement()), turnNumber, severity, category, message,
                Optional.ofNullable(location));
        eventBus.publish(new NotificationPublishedEvent(notification));
        return notification;
    }

    public List<GameNotification> history() { return List.copyOf(history); }
    public void clear() { history.clear(); }
    @Override public void close() { subscription.close(); }
}
