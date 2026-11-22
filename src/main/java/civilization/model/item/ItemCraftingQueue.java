package civilization.model.item;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

// PHASE 3 - Added in Phase 3
public final class ItemCraftingQueue implements Serializable {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private ItemCraftingOrder current;

    // PHASE 3 - Added in Phase 3
    public void enqueue(ItemCraftingOrder order) {
        if (current != null) throw new IllegalStateException("Apothecary crafting slot is occupied");
        current = Objects.requireNonNull(order, "order");
    }

    // PHASE 3 - Added in Phase 3
    public Optional<ItemCraftingOrder> currentOrder() { return Optional.ofNullable(current); }
    // PHASE 3 - Added in Phase 3
    public boolean isEmpty() { return current == null; }

    // PHASE 3 - Added in Phase 3
    public ItemCraftingOrder removeCompletedOrder() {
        if (current == null || !current.isComplete()) {
            throw new IllegalStateException("Crafting order is not complete");
        }
        ItemCraftingOrder completed = current;
        current = null;
        return completed;
    }
}
