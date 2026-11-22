package civilization.server.diplomacy;

import civilization.protocol.AllianceDecision;
import civilization.server.match.MultiplayerGameState;
import civilization.server.match.MultiplayerPlayerState;
import civilization.server.session.PlayerId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public final class PlayerDiplomacyService {
    // PHASE 3 - Added in Phase 3
    public static final PlayerDiplomaticRelation ALLIANCE_BREAK_RESULT =
            PlayerDiplomaticRelation.NEUTRAL;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;

    // PHASE 3 - Added in Phase 3
    public PlayerDiplomacyService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // PHASE 3 - Added in Phase 3
    public DiplomacyResult declareWar(MultiplayerGameState state, PlayerId actorId,
                                      String targetValue) {
        MultiplayerPlayerState actor = state.requirePlayer(actorId);
        MultiplayerPlayerState target = target(state, actorId, targetValue);
        if (state.diplomacy().relation(actorId, target.playerId())
                == PlayerDiplomaticRelation.ENEMY) {
            throw new DiplomacyException("ALREADY_AT_WAR", "Players are already enemies");
        }
        PlayerAllianceRequest cancelled = state.diplomacy()
                .pendingBetween(actorId, target.playerId()).stream().findFirst().orElse(null);
        state.diplomacy().declareWar(actorId, target.playerId());
        return result(DiplomacyActionKind.WAR_DECLARED, actor, target,
                PlayerDiplomaticRelation.ENEMY, cancelled);
    }

    // PHASE 3 - Added in Phase 3
    public DiplomacyResult requestAlliance(MultiplayerGameState state, PlayerId actorId,
                                           String targetValue) {
        MultiplayerPlayerState actor = state.requirePlayer(actorId);
        MultiplayerPlayerState target = target(state, actorId, targetValue);
        if (!state.connectedPlayers().contains(target.playerId())) {
            throw new DiplomacyException("DIPLOMACY_TARGET_DISCONNECTED", "Diplomacy target is disconnected");
        }
        if (state.diplomacy().relation(actorId, target.playerId())
                == PlayerDiplomaticRelation.ALLIED) {
            throw new DiplomacyException("ALREADY_ALLIED", "Players are already allied");
        }
        if (!state.diplomacy().pendingBetween(actorId, target.playerId()).isEmpty()) {
            throw new DiplomacyException("ALLIANCE_REQUEST_PENDING", "An alliance request is already pending between these players");
        }
        String requestId;
        // PHASE 3 - Added in Phase 3
        do { requestId = UUID.randomUUID().toString(); }
        while (state.diplomacy().containsRequest(requestId));
        PlayerAllianceRequest request = new PlayerAllianceRequest(requestId, actorId,
                target.playerId(), Instant.now(clock));
        state.diplomacy().addAllianceRequest(request);
        return result(DiplomacyActionKind.ALLIANCE_REQUESTED, actor, target,
                state.diplomacy().relation(actorId, target.playerId()), request);
    }

    // PHASE 3 - Added in Phase 3
    public DiplomacyResult decideAlliance(MultiplayerGameState state, PlayerId actorId,
                                          String requestId, AllianceDecision decision) {
        PlayerAllianceRequest request = state.diplomacy().requireRequest(requestId);
        if (request.status() != AllianceRequestStatus.PENDING) {
            throw new DiplomacyException("ALLIANCE_REQUEST_CLOSED", "Alliance request is no longer pending");
        }
        if (!request.targetPlayerId().equals(actorId)) {
            throw new DiplomacyException("UNAUTHORIZED_DIPLOMACY_ACTION", "Only the alliance target may decide this request");
        }
        MultiplayerPlayerState requester = state.requirePlayer(request.requesterPlayerId());
        MultiplayerPlayerState target = state.requirePlayer(actorId);
        if (decision == AllianceDecision.ACCEPT) {
            state.diplomacy().setAllied(requester.playerId(), target.playerId());
            request.close(AllianceRequestStatus.ACCEPTED);
            return result(DiplomacyActionKind.ALLIANCE_ACCEPTED, requester, target,
                    PlayerDiplomaticRelation.ALLIED, request);
        }
        request.close(AllianceRequestStatus.REJECTED);
        return result(DiplomacyActionKind.ALLIANCE_REJECTED, requester, target,
                state.diplomacy().relation(requester.playerId(), target.playerId()), request);
    }

    // PHASE 3 - Added in Phase 3
    public DiplomacyResult breakAlliance(MultiplayerGameState state, PlayerId actorId,
                                         String targetValue) {
        MultiplayerPlayerState actor = state.requirePlayer(actorId);
        MultiplayerPlayerState target = target(state, actorId, targetValue);
        if (state.diplomacy().relation(actorId, target.playerId())
                != PlayerDiplomaticRelation.ALLIED) {
            throw new DiplomacyException("NOT_ALLIED", "Only an existing alliance can be broken");
        }
        state.diplomacy().setNeutral(actorId, target.playerId());
        return result(DiplomacyActionKind.ALLIANCE_BROKEN, actor, target,
                ALLIANCE_BREAK_RESULT, null);
    }

    // PHASE 3 - Added in Phase 3
    private static MultiplayerPlayerState target(MultiplayerGameState state, PlayerId actorId,
                                                  String targetValue) {
        PlayerId targetId;
        try { targetId = new PlayerId(UUID.fromString(targetValue)); }
        catch (RuntimeException failure) {
            throw new DiplomacyException("INVALID_DIPLOMACY_TARGET", "Diplomacy target ID is invalid");
        }
        if (actorId.equals(targetId)) {
            throw new DiplomacyException("INVALID_DIPLOMACY_TARGET", "Players cannot target themselves with diplomacy");
        }
        try { return state.requirePlayer(targetId); }
        catch (IllegalArgumentException failure) {
            throw new DiplomacyException("INVALID_DIPLOMACY_TARGET", "Diplomacy target is not in this match");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static DiplomacyResult result(DiplomacyActionKind kind,
                                          MultiplayerPlayerState first,
                                          MultiplayerPlayerState second,
                                          PlayerDiplomaticRelation relation,
                                          PlayerAllianceRequest request) {
        return new DiplomacyResult(kind, first.playerId(), second.playerId(),
                first.username(), second.username(), relation, request,
                first.sessionId(), second.sessionId());
    }
}
