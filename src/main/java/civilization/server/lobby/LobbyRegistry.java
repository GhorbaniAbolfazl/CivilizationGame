package civilization.server.lobby;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// PHASE 3 - Added in Phase 3
public final class LobbyRegistry implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<LobbyId, LobbyRuntime> lobbies = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    public void add(LobbyId id, LobbyRuntime runtime) { if (lobbies.putIfAbsent(id, runtime) != null) throw new IllegalStateException("duplicate lobby"); }
    // PHASE 3 - Added in Phase 3
    public Optional<LobbyRuntime> find(LobbyId id) { return Optional.ofNullable(lobbies.get(id)); }
    // PHASE 3 - Added in Phase 3
    public LobbyRuntime require(LobbyId id) { return find(id).orElseThrow(() -> new LobbyException("LOBBY_NOT_FOUND", "Lobby does not exist")); }
    // PHASE 3 - Added in Phase 3
    public boolean remove(LobbyId id) { LobbyRuntime runtime = lobbies.remove(id); if (runtime == null) return false; runtime.close(); return true; }
    // PHASE 3 - Added in Phase 3
    public List<LobbyId> ids() { return List.copyOf(lobbies.keySet()); }
    // PHASE 3 - Added in Phase 3
    @Override public void close() { lobbies.values().forEach(LobbyRuntime::close); lobbies.clear(); }
}
