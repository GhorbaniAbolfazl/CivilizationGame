package civilization.protocol;

import java.util.Map;
import java.util.List;

// PHASE 3 - Added in Phase 3
public record GameSnapshotDto(
        // PHASE 3 - Added in Phase 3
        String matchId,
        // PHASE 3 - Added in Phase 3
        long turnNumber,
        // PHASE 3 - Added in Phase 3
        String activePlayerId,
        // PHASE 3 - Added in Phase 3
        String matchStatus,
        // PHASE 3 - Added in Phase 3
        String mapId,
        // PHASE 3 - Added in Phase 3
        String viewerPlayerId,
        // PHASE 3 - Added in Phase 3
        Map<String, Integer> viewerResources,
        // PHASE 3 - Added in Phase 3
        List<VisibleHexDto> visibleHexes,
        // PHASE 3 - Added in Phase 3
        List<VisibleUnitDto> visibleUnits,
        // PHASE 3 - Added in Phase 3
        List<VisibleBuildingDto> visibleBuildings,
        // PHASE 3 - Added in Phase 3
        List<InventoryItemDto> viewerItems,
        // PHASE 3 - Added in Phase 3
        List<ApothecaryQueueDto> apothecaryQueues,
        // PHASE 3 - Added in Phase 3
        Map<String, String> viewerAttributes) {
    // PHASE 3 - Added in Phase 3
    public GameSnapshotDto(String matchId, long turnNumber, String activePlayerId,
                           String matchStatus, Map<String, String> viewerAttributes) {
        this(matchId, turnNumber, activePlayerId, matchStatus, null, null,
                Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), viewerAttributes);
    }

    // PHASE 3 - Added in Phase 3
    public GameSnapshotDto {
        matchId = ProtocolValidation.id(matchId, "matchId");
        ProtocolValidation.nonNegative(turnNumber, "turnNumber");
        activePlayerId = ProtocolValidation.optionalText(activePlayerId, "activePlayerId", 128);
        matchStatus = ProtocolValidation.text(matchStatus, "matchStatus", 32);
        mapId = ProtocolValidation.optionalText(mapId, "mapId", 128);
        viewerPlayerId = ProtocolValidation.optionalText(viewerPlayerId, "viewerPlayerId", 128);
        viewerResources = ProtocolValidation.map(viewerResources, "viewerResources");
        // PHASE 3 - Added in Phase 3
        visibleHexes = ProtocolValidation.list(visibleHexes, "visibleHexes",
                ProtocolValidation.MAX_VISIBLE_HEXES);
        visibleUnits = ProtocolValidation.list(visibleUnits, "visibleUnits");
        visibleBuildings = ProtocolValidation.list(visibleBuildings, "visibleBuildings");
        viewerItems = ProtocolValidation.list(viewerItems, "viewerItems");
        apothecaryQueues = ProtocolValidation.list(apothecaryQueues, "apothecaryQueues");
        viewerAttributes = ProtocolValidation.map(viewerAttributes, "viewerAttributes");
    }
}
