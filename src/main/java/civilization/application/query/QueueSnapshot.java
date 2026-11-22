package civilization.application.query;

import java.util.Objects;

public record QueueSnapshot(String itemName, int remainingTurns) {
    public QueueSnapshot {
        Objects.requireNonNull(itemName, "itemName");
        if (itemName.isBlank() || remainingTurns < 0) {
            throw new IllegalArgumentException("Invalid queue snapshot");
        }
    }
}
