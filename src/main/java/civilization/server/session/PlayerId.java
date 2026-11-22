package civilization.server.session;

import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record PlayerId(
        // PHASE 3 - Added in Phase 3
        UUID value) {

    // PHASE 3 - Added in Phase 3
    public PlayerId {
        Objects.requireNonNull(value, "value");
    }

    // PHASE 3 - Added in Phase 3
    public static PlayerId create() {
        return new PlayerId(UUID.randomUUID());
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public String toString() {
        return value.toString();
    }
}
