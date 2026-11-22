package civilization.server.lobby;

import civilization.server.session.PlayerId;
import java.util.List;
import java.util.Objects;
import java.time.Instant;

// PHASE 3 - Added in Phase 3
public record LobbyView(LobbyId lobbyId, long version, PlayerId hostPlayerId,
                        String selectedMapId, LobbyLifecycle lifecycle, Instant createdAt,
                        List<LobbyMember> members) {
    // PHASE 3 - Added in Phase 3
    public LobbyView { Objects.requireNonNull(lobbyId); Objects.requireNonNull(lifecycle); Objects.requireNonNull(createdAt); members = List.copyOf(members); }
}
