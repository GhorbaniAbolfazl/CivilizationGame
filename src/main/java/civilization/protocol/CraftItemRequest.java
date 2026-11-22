package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record CraftItemRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String matchId,
        // PHASE 3 - Added in Phase 3
        String apothecaryId,
        // PHASE 3 - Added in Phase 3
        String itemType,
        // PHASE 3 - Added in Phase 3
        int quantity,
        // PHASE 3 - Added in Phase 3
        long observedStateVersion) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public CraftItemRequest {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        apothecaryId = ProtocolValidation.id(apothecaryId, "apothecaryId");
        itemType = ProtocolValidation.text(itemType, "itemType", 64);
        ProtocolValidation.positive(quantity, "quantity");
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.CRAFT_ITEM_REQUEST; }
}
