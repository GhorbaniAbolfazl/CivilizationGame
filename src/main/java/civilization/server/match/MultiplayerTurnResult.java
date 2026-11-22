package civilization.server.match;

import civilization.server.session.PlayerId;
import java.util.List;
import civilization.server.combat.PvpBattleReport;

// PHASE 3 - Added in Phase 3
public record MultiplayerTurnResult(long stateVersion, long turnNumber, PlayerId activePlayerId,
                                    List<String> processingTrace, List<String> expiredItemEffects,
                                    List<PvpBattleReport> battleReports) {
    // PHASE 3 - Added in Phase 3
    public MultiplayerTurnResult { processingTrace = List.copyOf(processingTrace); expiredItemEffects = List.copyOf(expiredItemEffects); battleReports = List.copyOf(battleReports); }
}
