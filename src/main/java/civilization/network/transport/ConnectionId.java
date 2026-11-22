package civilization.network.transport;

import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record ConnectionId(
        // PHASE 3 - Added in Phase 3
        UUID value) {
    // PHASE 3 - Added in Phase 3
    public ConnectionId {
        Objects.requireNonNull(value, "value");
    }

    // PHASE 3 - Added in Phase 3
    public static ConnectionId create() {
        return new ConnectionId(UUID.randomUUID());
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public String toString() {
        return value.toString();
    }
}
