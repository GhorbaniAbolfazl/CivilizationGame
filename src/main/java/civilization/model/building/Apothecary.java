package civilization.model.building;

import civilization.model.identity.FactionId;
import civilization.model.item.ItemCraftingQueue;
import civilization.model.map.HexCoord;

// PHASE 3 - Added in Phase 3
public final class Apothecary extends Building {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final ItemCraftingQueue craftingQueue;

    // PHASE 3 - Added in Phase 3
    public Apothecary(BuildingId id, HexCoord position, FactionId ownerId, int maximumHealth) {
        super(id, BuildingType.APOTHECARY, position, ownerId, maximumHealth);
        craftingQueue = new ItemCraftingQueue();
    }

    // PHASE 3 - Added in Phase 3
    public ItemCraftingQueue craftingQueue() { return craftingQueue; }
}
