package civilization.model.item;

import civilization.model.random.GameplayRandom;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public record ItemId(
        // PHASE 3 - Added in Phase 3
        UUID value) implements Serializable {
    // PHASE 3 - Added in Phase 3
    private static final long serialVersionUID = 1L;

    // PHASE 3 - Added in Phase 3
    public ItemId { Objects.requireNonNull(value, "value"); }

    // PHASE 3 - Added in Phase 3
    public static ItemId from(GameplayRandom random) {
        Objects.requireNonNull(random, "random");
        return new ItemId(new UUID(random.nextLong(), random.nextLong()));
    }
}
