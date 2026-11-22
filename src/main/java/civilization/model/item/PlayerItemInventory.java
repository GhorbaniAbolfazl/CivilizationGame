package civilization.model.item;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public final class PlayerItemInventory implements Serializable {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;
    // PHASE 3 - Added in Phase 3
    private final Map<ItemId, ItemInstance> items = new LinkedHashMap<>();

    // PHASE 3 - Added in Phase 3
    public void add(ItemInstance item) {
        Objects.requireNonNull(item, "item");
        if (items.putIfAbsent(item.id(), item) != null) {
            throw new IllegalArgumentException("Duplicate item ID: " + item.id());
        }
    }

    // PHASE 3 - Added in Phase 3
    public ItemInstance require(ItemId id) {
        ItemInstance item = items.get(Objects.requireNonNull(id, "id"));
        if (item == null) throw new IllegalArgumentException("Unknown item: " + id.value());
        return item;
    }

    // PHASE 3 - Added in Phase 3
    public ItemInstance remove(ItemId id) {
        ItemInstance item = items.remove(Objects.requireNonNull(id, "id"));
        if (item == null) throw new IllegalArgumentException("Unknown item: " + id.value());
        return item;
    }

    // PHASE 3 - Added in Phase 3
    public Collection<ItemInstance> items() {
        return Collections.unmodifiableCollection(items.values());
    }
}
