package civilization.server.diplomacy;

import civilization.protocol.AllianceRequestPush;
import civilization.protocol.DiplomacyStatePush;
import civilization.protocol.MessageMetadata;

// PHASE 3 - Added in Phase 3
public final class DiplomacyProtocolMapper {
    // PHASE 3 - Added in Phase 3
    public DiplomacyStatePush relation(String matchId, long stateVersion,
                                       DiplomaticPairView pair) {
        return new DiplomacyStatePush(MessageMetadata.createNow(), matchId, stateVersion,
                pair.firstPlayerId().toString(), pair.secondPlayerId().toString(),
                pair.relation().name());
    }

    // PHASE 3 - Added in Phase 3
    public DiplomacyStatePush relation(String matchId, long stateVersion,
                                       DiplomacyResult result) {
        return new DiplomacyStatePush(MessageMetadata.createNow(), matchId, stateVersion,
                result.firstPlayerId().toString(), result.secondPlayerId().toString(),
                result.relation().name());
    }

    // PHASE 3 - Added in Phase 3
    public AllianceRequestPush allianceRequest(String matchId, long stateVersion,
                                                PlayerAllianceRequest request) {
        return new AllianceRequestPush(MessageMetadata.createNow(), matchId, stateVersion,
                request.requestId(), request.requesterPlayerId().toString(),
                request.targetPlayerId().toString(), request.status().name(),
                request.createdAt());
    }
}
