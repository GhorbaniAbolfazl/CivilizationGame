package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record ApothecaryQueueDto(
        // PHASE 3 - Added in Phase 3
        String apothecaryId,
        // PHASE 3 - Added in Phase 3
        String itemType,
        // PHASE 3 - Added in Phase 3
        int remainingTurns) {
    // PHASE 3 - Added in Phase 3
    public ApothecaryQueueDto {
        apothecaryId = ProtocolValidation.id(apothecaryId, "apothecaryId");
        itemType = ProtocolValidation.text(itemType, "itemType", 32);
        ProtocolValidation.nonNegative(remainingTurns, "remainingTurns");
    }
}
