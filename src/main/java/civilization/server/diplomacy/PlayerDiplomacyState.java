package civilization.server.diplomacy;

import civilization.server.session.PlayerId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class PlayerDiplomacyState {
    // PHASE 3 - Added in Phase 3
    private final Map<PlayerPair, PlayerDiplomaticRelation> relations = new LinkedHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final Map<String, PlayerAllianceRequest> allianceRequests = new LinkedHashMap<>();

    // PHASE 3 - Added in Phase 3
    public PlayerDiplomacyState(List<PlayerId> players) {
        List<PlayerId> trusted = List.copyOf(Objects.requireNonNull(players, "players"));
        // PHASE 3 - Added in Phase 3
        for (int left = 0; left < trusted.size(); left++) {
            for (int right = left + 1; right < trusted.size(); right++) {
                relations.put(PlayerPair.of(trusted.get(left), trusted.get(right)),
                        PlayerDiplomaticRelation.NEUTRAL);
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    public PlayerDiplomaticRelation relation(PlayerId first, PlayerId second) {
        if (Objects.equals(first, second)) {
            throw new IllegalArgumentException("A player has no diplomacy relation with itself");
        }
        PlayerDiplomaticRelation relation = relations.get(PlayerPair.of(first, second));
        if (relation == null) throw new IllegalArgumentException("Unknown player pair");
        return relation;
    }

    // PHASE 3 - Added in Phase 3
    public void declareWar(PlayerId first, PlayerId second) {
        PlayerPair pair = PlayerPair.of(first, second);
        if (!relations.containsKey(pair)) throw new IllegalArgumentException("Unknown player pair");
        relations.put(pair, PlayerDiplomaticRelation.ENEMY);
        // PHASE 3 - Added in Phase 3
        pendingBetween(first, second).forEach(request -> request.close(AllianceRequestStatus.CANCELLED));
    }

    // PHASE 3 - Added in Phase 3
    public void setAllied(PlayerId first, PlayerId second) {
        PlayerPair pair = PlayerPair.of(first, second);
        if (!relations.containsKey(pair)) throw new IllegalArgumentException("Unknown player pair");
        relations.put(pair, PlayerDiplomaticRelation.ALLIED);
    }

    // PHASE 3 - Added in Phase 3
    public void setNeutral(PlayerId first, PlayerId second) {
        PlayerPair pair = PlayerPair.of(first, second);
        if (!relations.containsKey(pair)) throw new IllegalArgumentException("Unknown player pair");
        relations.put(pair, PlayerDiplomaticRelation.NEUTRAL);
    }

    // PHASE 3 - Added in Phase 3
    public void addAllianceRequest(PlayerAllianceRequest request) {
        Objects.requireNonNull(request, "request");
        if (allianceRequests.putIfAbsent(request.requestId(), request) != null) {
            throw new IllegalStateException("Duplicate alliance request ID");
        }
    }

    // PHASE 3 - Added in Phase 3
    public boolean containsRequest(String requestId) { return allianceRequests.containsKey(requestId); }

    // PHASE 3 - Added in Phase 3
    public PlayerAllianceRequest requireRequest(String requestId) {
        PlayerAllianceRequest request = allianceRequests.get(requestId);
        if (request == null) {
            throw new DiplomacyException("ALLIANCE_REQUEST_NOT_FOUND", "Alliance request does not exist");
        }
        return request;
    }

    // PHASE 3 - Added in Phase 3
    public List<PlayerAllianceRequest> allianceRequests() {
        return List.copyOf(allianceRequests.values());
    }

    // PHASE 3 - Added in Phase 3
    public List<PlayerAllianceRequest> pendingBetween(PlayerId first, PlayerId second) {
        PlayerPair pair = PlayerPair.of(first, second);
        return allianceRequests.values().stream()
                .filter(request -> request.status() == AllianceRequestStatus.PENDING)
                .filter(request -> PlayerPair.of(request.requesterPlayerId(),
                        request.targetPlayerId()).equals(pair)).toList();
    }

    // PHASE 3 - Added in Phase 3
    public List<DiplomaticPairView> relations() {
        return relations.entrySet().stream().map(entry -> new DiplomaticPairView(
                entry.getKey().first(), entry.getKey().second(), entry.getValue())).toList();
    }

    // PHASE 3 - Added in Phase 3
    public List<PlayerId> alliesOf(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return relations.entrySet().stream()
                .filter(entry -> entry.getValue() == PlayerDiplomaticRelation.ALLIED)
                .filter(entry -> entry.getKey().first().equals(playerId)
                        || entry.getKey().second().equals(playerId))
                .map(entry -> entry.getKey().first().equals(playerId)
                        ? entry.getKey().second() : entry.getKey().first()).toList();
    }

    // PHASE 3 - Added in Phase 3
    private record PlayerPair(PlayerId first, PlayerId second) {
        // PHASE 3 - Added in Phase 3
        private static PlayerPair of(PlayerId first, PlayerId second) {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            return first.toString().compareTo(second.toString()) <= 0
                    ? new PlayerPair(first, second) : new PlayerPair(second, first);
        }
    }
}
