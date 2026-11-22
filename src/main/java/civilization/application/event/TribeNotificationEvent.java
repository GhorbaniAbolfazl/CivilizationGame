package civilization.application.event;

import civilization.application.notification.NotificationCategory;
import civilization.model.identity.TribeId;
import civilization.model.map.HexCoord;
import java.util.Objects;
import java.util.Optional;

/** Visibility-safe, Swing-free notification emitted by tribe use cases and turn behavior. */
public record TribeNotificationEvent(
        TribeId tribeId,
        int turnNumber,
        NotificationCategory category,
        String message,
        Optional<HexCoord> location) implements GameEvent {
    public TribeNotificationEvent {
        Objects.requireNonNull(tribeId, "tribeId");
        if (turnNumber < 1) throw new IllegalArgumentException("Turn number must be positive");
        Objects.requireNonNull(category, "category");
        message = Objects.requireNonNull(message, "message").trim();
        if (message.isEmpty()) throw new IllegalArgumentException("Message cannot be blank");
        location = Objects.requireNonNull(location, "location");
    }
}
