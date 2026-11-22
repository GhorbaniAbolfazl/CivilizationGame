package civilization.server.session;

import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record SessionId(
        // PHASE 3 - Added in Phase 3
        UUID value) {

    // PHASE 3 - Added in Phase 3
    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    // PHASE 3 - Added in Phase 3
    public static SessionId create() {
        return new SessionId(UUID.randomUUID());
    }

    // PHASE 3 - Added in Phase 3
    public static SessionId parse(String value) {
        try {
            return new SessionId(UUID.fromString(Objects.requireNonNull(value, "value")));
        } catch (IllegalArgumentException failure) {
            throw new SessionException("INVALID_SESSION", "Session ID is invalid");
        }
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public String toString() {
        return value.toString();
    }
}
