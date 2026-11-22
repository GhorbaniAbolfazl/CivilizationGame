package civilization.server.match;

import civilization.protocol.GameSnapshotDto;
import civilization.server.session.PlayerId;
import civilization.server.session.SessionId;
import java.util.Map;

// PHASE 3 - Added in Phase 3
public record MultiplayerStateUpdate(String matchId, long stateVersion, long turnNumber,
                                     PlayerId activePlayerId, String phase,
                                     Map<SessionId, GameSnapshotDto> clientSnapshots) {
    // PHASE 3 - Added in Phase 3
    public MultiplayerStateUpdate { clientSnapshots = Map.copyOf(clientSnapshots); }
}
