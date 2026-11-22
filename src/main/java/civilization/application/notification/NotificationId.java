package civilization.application.notification;

public record NotificationId(long value) {
    public NotificationId { if (value <= 0) throw new IllegalArgumentException("Notification ID must be positive"); }
}
