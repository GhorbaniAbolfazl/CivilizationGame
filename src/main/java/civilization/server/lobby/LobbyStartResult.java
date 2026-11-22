package civilization.server.lobby;

import civilization.server.session.SessionId;
import java.util.List;

// PHASE 3 - Added in Phase 3
public record LobbyStartResult(LobbyView view, List<SessionId> recipients, MatchLaunch launch) {
    // PHASE 3 - Added in Phase 3
    public LobbyStartResult { recipients = List.copyOf(recipients); }
}
