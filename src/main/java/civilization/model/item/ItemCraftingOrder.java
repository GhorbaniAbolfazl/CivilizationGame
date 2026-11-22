package civilization.model.item;

import civilization.model.economy.ResourceBundle;
import java.io.Serializable;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class ItemCraftingOrder implements Serializable {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final ItemType itemType;
    // PHASE 3 - Added in Phase 3
    private final ResourceBundle paidCost;
    // PHASE 3 - Added in Phase 3
    private int remainingTurns = 1;

    // PHASE 3 - Added in Phase 3
    public ItemCraftingOrder(ItemType itemType, ResourceBundle paidCost) {
        this.itemType = Objects.requireNonNull(itemType, "itemType");
        this.paidCost = Objects.requireNonNull(paidCost, "paidCost");
    }

    // PHASE 3 - Added in Phase 3
    public ItemType itemType() { return itemType; }
    // PHASE 3 - Added in Phase 3
    public ResourceBundle paidCost() { return paidCost; }
    // PHASE 3 - Added in Phase 3
    public int remainingTurns() { return remainingTurns; }
    // PHASE 3 - Added in Phase 3
    public boolean isComplete() { return remainingTurns == 0; }

    // PHASE 3 - Added in Phase 3
    public void advanceOneTurn() {
        if (isComplete()) throw new IllegalStateException("Crafting order already completed");
        remainingTurns--;
    }
}
