package civilization.server.item;

import civilization.model.building.BuildingId;
import civilization.model.economy.ResourceBundle;
import civilization.model.item.ItemType;
import civilization.server.session.PlayerId;

// PHASE 3 - Added in Phase 3
public record ItemCraftResult(
        // PHASE 3 - Added in Phase 3
        PlayerId playerId,
        // PHASE 3 - Added in Phase 3
        BuildingId apothecaryId,
        // PHASE 3 - Added in Phase 3
        ItemType itemType,
        // PHASE 3 - Added in Phase 3
        ResourceBundle paidCost) { }
