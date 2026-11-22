package civilization.server.match;

import civilization.server.session.PlayerId;

// PHASE 3 - Added in Phase 3
public record PlayerEliminationResult(
        // PHASE 3 - Added in Phase 3
        boolean eliminated,
        // PHASE 3 - Added in Phase 3
        PlayerId eliminatedPlayerId,
        // PHASE 3 - Added in Phase 3
        boolean matchFinished,
        // PHASE 3 - Added in Phase 3
        PlayerId winnerPlayerId) {
}
