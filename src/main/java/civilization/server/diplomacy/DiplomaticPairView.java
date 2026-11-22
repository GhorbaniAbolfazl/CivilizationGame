package civilization.server.diplomacy;

import civilization.server.session.PlayerId;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record DiplomaticPairView(PlayerId firstPlayerId, PlayerId secondPlayerId,
                                 PlayerDiplomaticRelation relation) {
    // PHASE 3 - Added in Phase 3
    public DiplomaticPairView {
        Objects.requireNonNull(firstPlayerId, "firstPlayerId");
        Objects.requireNonNull(secondPlayerId, "secondPlayerId");
        Objects.requireNonNull(relation, "relation");
    }
}
