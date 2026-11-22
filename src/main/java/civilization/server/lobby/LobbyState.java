package civilization.server.lobby;

import civilization.server.map.PredefinedMapCatalog;
import civilization.server.map.PredefinedMapDefinition;
import civilization.server.session.ClientSession;
import civilization.server.session.PlayerId;
import civilization.server.session.SessionId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.Instant;

// PHASE 3 - Added in Phase 3
final class LobbyState {
    // PHASE 3 - Added in Phase 3
    private final LobbyId id;
    // PHASE 3 - Added in Phase 3
    private final Instant createdAt;
    // PHASE 3 - Added in Phase 3
    private final LinkedHashMap<PlayerId, LobbyMember> members = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private PlayerId host;
    // PHASE 3 - Added in Phase 3
    private String selectedMapId;
    // PHASE 3 - Added in Phase 3
    private LobbyLifecycle lifecycle = LobbyLifecycle.OPEN;
    // PHASE 3 - Added in Phase 3
    private long version = 1;

    // PHASE 3 - Added in Phase 3
    LobbyState(LobbyId id, Instant createdAt, ClientSession creator) { this.id = id; this.createdAt = createdAt; add(creator); host = creator.playerId(); }

    // PHASE 3 - Added in Phase 3
    LobbyChange join(ClientSession session) { requireOpen(); add(session); version++; return change(); }
    // PHASE 3 - Added in Phase 3
    private void add(ClientSession session) {
        // PHASE 3 - Added in Phase 3
        if (members.size() >= 6) throw error("LOBBY_FULL", "Lobby has reached its maximum player count");
        if (members.containsKey(session.playerId())) throw error("ALREADY_IN_LOBBY", "Player is already in this lobby");
        members.put(session.playerId(), new LobbyMember(session.sessionId(), session.playerId(), session.username(), session.createdAt(), false, true));
    }
    // PHASE 3 - Added in Phase 3
    LobbyChange leave(PlayerId actor) {
        requireOpen();
        if (members.remove(actor) == null) throw error("NOT_IN_LOBBY", "Player is not in this lobby");
        if (members.isEmpty()) { host = null; lifecycle = LobbyLifecycle.CLOSED; }
        else if (actor.equals(host)) host = members.values().stream().min(Comparator.comparing(LobbyMember::createdAt).thenComparing(m -> m.playerId().toString())).orElseThrow().playerId();
        version++; return change();
    }
    // PHASE 3 - Added in Phase 3
    LobbyChange ready(PlayerId actor, boolean ready) { requireOpen(); LobbyMember member = requireMember(actor); members.put(actor, member.withReady(ready)); version++; return change(); }
    // PHASE 3 - Added in Phase 3
    LobbyChange selectMap(PlayerId actor, String mapId, PredefinedMapCatalog maps) {
        requireOpen(); requireHost(actor); maps.require(mapId); selectedMapId = mapId; version++; return change();
    }
    // PHASE 3 - Added in Phase 3
    LobbyStartResult start(PlayerId actor, PredefinedMapCatalog maps, MatchLaunchFactory launches) {
        requireOpen(); requireHost(actor);
        if (selectedMapId == null) throw error("MAP_REQUIRED", "Host must select a map");
        PredefinedMapDefinition map = maps.require(selectedMapId);
        if (!map.supports(members.size())) throw error("INVALID_PLAYER_COUNT", "Selected map does not support this player count");
        if (members.values().stream().anyMatch(member -> !member.connected() || !member.ready())) throw error("PLAYERS_NOT_READY", "Every connected player must be ready");
        MatchLaunch launch = launches.create(id, new ArrayList<>(members.values()), map);
        lifecycle = LobbyLifecycle.STARTED; version++;
        return new LobbyStartResult(view(), recipients(), launch);
    }
    // PHASE 3 - Added in Phase 3
    LobbyView view() { return new LobbyView(id, version, host, selectedMapId, lifecycle, createdAt, new ArrayList<>(members.values())); }
    // PHASE 3 - Added in Phase 3
    private LobbyChange change() { return new LobbyChange(view(), recipients()); }
    // PHASE 3 - Added in Phase 3
    private List<SessionId> recipients() { return members.values().stream().map(LobbyMember::sessionId).toList(); }
    // PHASE 3 - Added in Phase 3
    private LobbyMember requireMember(PlayerId actor) { LobbyMember m = members.get(actor); if (m == null) throw error("NOT_IN_LOBBY", "Player is not in this lobby"); return m; }
    // PHASE 3 - Added in Phase 3
    private void requireHost(PlayerId actor) { requireMember(actor); if (!actor.equals(host)) throw error("HOST_ONLY", "Only the host may perform this action"); }
    // PHASE 3 - Added in Phase 3
    private void requireOpen() { if (lifecycle != LobbyLifecycle.OPEN) throw error("LOBBY_NOT_OPEN", "Lobby is not open"); }
    // PHASE 3 - Added in Phase 3
    private static LobbyException error(String code, String message) { return new LobbyException(code, message); }
}
