package civilization.server.diplomacy;

import civilization.server.session.PlayerId;
import civilization.server.session.SessionId;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record DiplomacyResult(DiplomacyActionKind kind, PlayerId firstPlayerId,
                              PlayerId secondPlayerId, String firstUsername,
                              String secondUsername, PlayerDiplomaticRelation relation,
                              PlayerAllianceRequest allianceRequest,
                              SessionId firstSessionId, SessionId secondSessionId) {
    // PHASE 3 - Added in Phase 3
    public DiplomacyResult {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(firstPlayerId, "firstPlayerId");
        Objects.requireNonNull(secondPlayerId, "secondPlayerId");
        Objects.requireNonNull(firstUsername, "firstUsername");
        Objects.requireNonNull(secondUsername, "secondUsername");
        Objects.requireNonNull(relation, "relation");
        // PHASE 3 - Added in Phase 3
        if (allianceRequest != null) allianceRequest = allianceRequest.snapshot();
        Objects.requireNonNull(firstSessionId, "firstSessionId");
        Objects.requireNonNull(secondSessionId, "secondSessionId");
    }
}
