package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record VisibleUnitDto(String unitId, String unitType, String ownerPlayerId,
                             int q, int r, int remainingActionPoints) {
    // PHASE 3 - Added in Phase 3
    public VisibleUnitDto {
        unitId = ProtocolValidation.id(unitId, "unitId");
        unitType = ProtocolValidation.text(unitType, "unitType", 32);
        ownerPlayerId = ProtocolValidation.id(ownerPlayerId, "ownerPlayerId");
        ProtocolValidation.nonNegative(remainingActionPoints, "remainingActionPoints");
    }
}
