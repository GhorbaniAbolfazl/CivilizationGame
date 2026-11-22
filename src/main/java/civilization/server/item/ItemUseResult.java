package civilization.server.item;

import civilization.model.item.ItemId;
import civilization.model.item.ItemType;
import civilization.model.map.HexCoord;
import civilization.model.unit.UnitId;
import civilization.server.session.PlayerId;

// PHASE 3 - Added in Phase 3
public record ItemUseResult(
        // PHASE 3 - Added in Phase 3
        PlayerId playerId,
        // PHASE 3 - Added in Phase 3
        ItemId itemId,
        // PHASE 3 - Added in Phase 3
        ItemType itemType,
        // PHASE 3 - Added in Phase 3
        UnitId targetUnitId,
        // PHASE 3 - Added in Phase 3
        HexCoord destination) { }
