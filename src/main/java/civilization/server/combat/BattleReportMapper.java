package civilization.server.combat;

import civilization.protocol.BattleReportPush;
import civilization.protocol.MessageMetadata;

// PHASE 3 - Added in Phase 3
public final class BattleReportMapper {
    // PHASE 3 - Added in Phase 3
    public BattleReportPush toPush(long stateVersion, PvpBattleReport report) {
        return new BattleReportPush(MessageMetadata.createNow(), stateVersion,
                report.battleId(), report.attackerPlayerId().toString(),
                report.defenderPlayerId().toString(), report.targetId(), report.outcome(),
                report.remainingHitPoints(), report.attackerRolls(), report.defenderRolls(),
                report.lostUnitIds(), report.structureDamage(), report.createdAt());
    }
}
