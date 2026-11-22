package civilization.server.match;

import java.util.Optional;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import civilization.server.session.SessionId;

// PHASE 3 - Added in Phase 3
public final class MultiplayerMatchRegistry implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<String, MultiplayerGameRuntime> matches = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<SessionId, String> matchesBySession = new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    public void add(MultiplayerGameState match) {
        MultiplayerGameRuntime runtime = new MultiplayerGameRuntime(match);
        if (matches.putIfAbsent(match.matchId(), runtime) != null) { runtime.close(); throw new IllegalStateException("duplicate match"); }
        // PHASE 3 - Added in Phase 3
        match.players().values().forEach(player -> matchesBySession.put(player.sessionId(), match.matchId()));
    }
    // PHASE 3 - Added in Phase 3
    public Optional<MultiplayerGameRuntime> find(String matchId) { return Optional.ofNullable(matches.get(matchId)); }
    // PHASE 3 - Added in Phase 3
    public MultiplayerGameRuntime require(String matchId) { return find(matchId).orElseThrow(() -> new IllegalArgumentException("unknown match")); }
    // PHASE 3 - Added in Phase 3
    public MultiplayerGameRuntime requireFor(SessionId sessionId, String matchId) {
        if (!matchId.equals(matchesBySession.get(sessionId))) throw new MatchException("NOT_MATCH_PLAYER", "Session does not belong to this match");
        return require(matchId);
    }
    // PHASE 3 - Added in Phase 3
    public Optional<MultiplayerGameRuntime> findFor(SessionId sessionId) { String id = matchesBySession.get(sessionId); return id == null ? Optional.empty() : find(id); }
    // PHASE 3 - Added in Phase 3
    public List<SessionId> requireMemberSessions(SessionId sender, String matchId) {
        requireFor(sender, matchId);
        return matchesBySession.entrySet().stream()
                .filter(entry -> entry.getValue().equals(matchId))
                .map(java.util.Map.Entry::getKey)
                .toList();
    }
    // PHASE 3 - Added in Phase 3
    @Override public void close() { matches.values().forEach(MultiplayerGameRuntime::close); matches.clear(); matchesBySession.clear(); }
}
