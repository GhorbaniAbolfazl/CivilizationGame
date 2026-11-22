package civilization.model.item;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record ItemInstance(
        // PHASE 3 - Added in Phase 3
        ItemId id,
        // PHASE 3 - Added in Phase 3
        ItemType type,
        // PHASE 3 - Added in Phase 3
        Instant createdAt) implements Serializable {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;

    // PHASE 3 - Added in Phase 3
    public ItemInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
