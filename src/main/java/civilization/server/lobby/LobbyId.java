package civilization.server.lobby;

import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record LobbyId(UUID value) {
    // PHASE 3 - Added in Phase 3
    public LobbyId { Objects.requireNonNull(value, "value"); }
    // PHASE 3 - Added in Phase 3
    public static LobbyId create() { return new LobbyId(UUID.randomUUID()); }
    // PHASE 3 - Added in Phase 3
    public static LobbyId parse(String value) { return new LobbyId(UUID.fromString(value)); }
    // PHASE 3 - Added in Phase 3
    @Override public String toString() { return value.toString(); }
}
