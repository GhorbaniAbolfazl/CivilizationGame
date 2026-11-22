package civilization.server.match;

import civilization.server.session.PlayerId;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record PlayerConnectionChange(boolean changed, PlayerId playerId, String username,
                                     boolean turnAutoResolved) {
    // PHASE 3 - Added in Phase 3
    public PlayerConnectionChange {
        Objects.requireNonNull(playerId, "playerId");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required");
    }
}
