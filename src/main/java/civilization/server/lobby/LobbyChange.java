package civilization.server.lobby;

import civilization.server.session.SessionId;
import java.util.List;

// PHASE 3 - Added in Phase 3
public record LobbyChange(LobbyView view, List<SessionId> recipients) {
    // PHASE 3 - Added in Phase 3
    public LobbyChange { recipients = List.copyOf(recipients); }
}
