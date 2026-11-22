package civilization.protocol;

import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record LobbyMemberDto(
        // PHASE 3 - Added in Phase 3
        String playerId,
        // PHASE 3 - Added in Phase 3
        String username,
        // PHASE 3 - Added in Phase 3
        Instant createdAt,
        // PHASE 3 - Added in Phase 3
        boolean ready,
        // PHASE 3 - Added in Phase 3
        boolean connected) {
    // PHASE 3 - Added in Phase 3
    public LobbyMemberDto {
        playerId = ProtocolValidation.id(playerId, "playerId");
        username = ProtocolValidation.text(username, "username", 64);
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
