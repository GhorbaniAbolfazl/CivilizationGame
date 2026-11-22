package civilization.application.event;

import civilization.application.notification.GameNotification;
import java.util.Objects;

public record NotificationPublishedEvent(GameNotification notification) implements GameEvent {
    public NotificationPublishedEvent { Objects.requireNonNull(notification, "notification"); }
}
