package civilization.application.notification;

import civilization.model.map.HexCoord;
import java.util.Objects;
import java.util.Optional;

public record GameNotification(
        NotificationId id,
        int turnNumber,
        NotificationSeverity severity,
        NotificationCategory category,
        String message,
        Optional<HexCoord> location) {
    public GameNotification {
        Objects.requireNonNull(id, "id");
        if (turnNumber < 1) throw new IllegalArgumentException("Turn number must be positive");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) throw new IllegalArgumentException("Notification message cannot be blank");
        location = Objects.requireNonNull(location, "location");
    }
}
