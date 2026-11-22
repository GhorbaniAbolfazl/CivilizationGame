package civilization.server.map;

import civilization.model.economy.ResourceType;
import java.util.Map;

// PHASE 3 - Added in Phase 3
public record PredefinedMapValidationReport(
        // PHASE 3 - Added in Phase 3
        String mapId,
        // PHASE 3 - Added in Phase 3
        int tileCount,
        // PHASE 3 - Added in Phase 3
        int traversableTileCount,
        // PHASE 3 - Added in Phase 3
        int seaTileCount,
        // PHASE 3 - Added in Phase 3
        int mountainTileCount,
        // PHASE 3 - Added in Phase 3
        int mountainRangeTileCount,
        // PHASE 3 - Added in Phase 3
        int riverEdgeCount,
        // PHASE 3 - Added in Phase 3
        Map<Integer, Integer> minimumSpawnSpacing,
        // PHASE 3 - Added in Phase 3
        Map<Integer, Map<ResourceType, Integer>> maximumResourceDistances) {
}
