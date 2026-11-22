package civilization.server.lobby;

import civilization.server.map.PredefinedMapCatalog;
import civilization.server.match.SerializedServerState;
import civilization.server.session.ClientSession;
import civilization.server.session.PlayerId;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;

// PHASE 3 - Added in Phase 3
public final class LobbyRuntime implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    private final SerializedServerState<LobbyState> state;
    // PHASE 3 - Added in Phase 3
    private final PredefinedMapCatalog maps;
    // PHASE 3 - Added in Phase 3
    private final MatchLaunchFactory launches;

    // PHASE 3 - Added in Phase 3
    public LobbyRuntime(LobbyId id, Instant createdAt, ClientSession creator, PredefinedMapCatalog maps, MatchLaunchFactory launches) {
        this.maps = maps; this.launches = launches;
        state = new SerializedServerState<>(new LobbyState(id, createdAt, creator), "lobby-" + id);
    }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyView> view() { return state.submit(LobbyState::view); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyChange> join(ClientSession session) { return state.submit(value -> value.join(session)); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyChange> leave(PlayerId player) { return state.submit(value -> value.leave(player)); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyChange> ready(PlayerId player, boolean ready) { return state.submit(value -> value.ready(player, ready)); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyChange> selectMap(PlayerId player, String mapId) { return state.submit(value -> value.selectMap(player, mapId, maps)); }
    // PHASE 3 - Added in Phase 3
    public CompletableFuture<LobbyStartResult> start(PlayerId player) { return state.submit(value -> value.start(player, maps, launches)); }
    // PHASE 3 - Added in Phase 3
    @Override public void close() { state.close(); }
}
