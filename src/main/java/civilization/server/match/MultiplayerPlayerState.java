package civilization.server.match;

import civilization.model.player.PlayerState;
import civilization.server.session.PlayerId;
import civilization.server.session.SessionId;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record MultiplayerPlayerState(SessionId sessionId, PlayerId playerId, String username,
                                     PlayerState empire) {
    // PHASE 3 - Added in Phase 3
    public MultiplayerPlayerState { Objects.requireNonNull(sessionId); Objects.requireNonNull(playerId); Objects.requireNonNull(empire); if (username == null || username.isBlank()) throw new IllegalArgumentException("username is required"); }
}
