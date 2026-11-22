package civilization.server.lobby;

import civilization.server.session.PlayerId;
import civilization.server.session.SessionId;
import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record LobbyMember(SessionId sessionId, PlayerId playerId, String username,
                          Instant createdAt, boolean ready, boolean connected) {
    // PHASE 3 - Added in Phase 3
    public LobbyMember {
        Objects.requireNonNull(sessionId, "sessionId"); Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required");
    }
    // PHASE 3 - Added in Phase 3
    public LobbyMember withReady(boolean value) { return new LobbyMember(sessionId, playerId, username, createdAt, value, connected); }
}
