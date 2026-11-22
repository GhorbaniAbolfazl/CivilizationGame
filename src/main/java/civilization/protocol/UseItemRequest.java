package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record UseItemRequest(
        // PHASE 3 - Added in Phase 3
        MessageMetadata metadata,
        // PHASE 3 - Added in Phase 3
        String matchId,
        // PHASE 3 - Added in Phase 3
        String itemId,
        // PHASE 3 - Added in Phase 3
        String targetId,
        // PHASE 3 - Added in Phase 3
        HexCoordinate targetLocation,
        // PHASE 3 - Added in Phase 3
        long observedStateVersion) implements RequestMessage {
    // PHASE 3 - Added in Phase 3
    public UseItemRequest {
        ProtocolValidation.metadata(metadata);
        matchId = ProtocolValidation.id(matchId, "matchId");
        itemId = ProtocolValidation.id(itemId, "itemId");
        targetId = ProtocolValidation.optionalText(targetId, "targetId", 128);
        if (targetId == null && targetLocation == null) {
            throw new IllegalArgumentException("An item target ID or location is required");
        }
        ProtocolValidation.nonNegative(observedStateVersion, "observedStateVersion");
    }

    // PHASE 3 - Added in Phase 3
    @Override public MessageType type() { return MessageType.USE_ITEM_REQUEST; }
}
