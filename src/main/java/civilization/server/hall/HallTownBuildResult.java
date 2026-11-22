package civilization.server.hall;

import civilization.model.building.BuildingId;
import civilization.model.economy.ResourceBundle;
import civilization.model.map.HexCoord;
import civilization.server.session.PlayerId;
import java.util.Objects;
import java.util.Set;

// PHASE 3 - Added in Phase 3
public record HallTownBuildResult(
        // PHASE 3 - Added in Phase 3
        PlayerId playerId,
        // PHASE 3 - Added in Phase 3
        BuildingId hallId,
        // PHASE 3 - Added in Phase 3
        HexCoord position,
        // PHASE 3 - Added in Phase 3
        ResourceBundle cost,
        // PHASE 3 - Added in Phase 3
        Set<HexCoord> controlledRegion) {
    // PHASE 3 - Added in Phase 3
    public HallTownBuildResult {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(hallId, "hallId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(cost, "cost");
        controlledRegion = Set.copyOf(controlledRegion);
    }
}
