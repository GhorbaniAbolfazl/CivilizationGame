package civilization.server.lobby;

import civilization.server.map.PredefinedMapDefinition;
import civilization.server.session.PlayerId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import civilization.server.match.MultiplayerGameState;

// PHASE 3 - Added in Phase 3
public record MatchLaunch(String matchId, LobbyId lobbyId, PredefinedMapDefinition map,
                          PlayerId activePlayerId, List<LobbyMember> players,
                          Map<PlayerId, InitialPlayerSetup> setups, Instant createdAt,
                          MultiplayerGameState game) {
    // PHASE 3 - Added in Phase 3
    public MatchLaunch { players = List.copyOf(players); setups = Map.copyOf(setups); java.util.Objects.requireNonNull(game); }
}
