package civilization.protocol;

import java.time.Instant;

// PHASE 3 - Added in Phase 3
public record InventoryItemDto(
        // PHASE 3 - Added in Phase 3
        String itemId,
        // PHASE 3 - Added in Phase 3
        String itemType,
        // PHASE 3 - Added in Phase 3
        Instant createdAt) {
    // PHASE 3 - Added in Phase 3
    public InventoryItemDto {
        itemId = ProtocolValidation.id(itemId, "itemId");
        itemType = ProtocolValidation.text(itemType, "itemType", 32);
        if (createdAt == null) throw new NullPointerException("createdAt");
    }
}
