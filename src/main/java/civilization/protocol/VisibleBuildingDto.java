package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record VisibleBuildingDto(String buildingId, String buildingType, String ownerPlayerId,
                                 int q, int r, String status) {
    // PHASE 3 - Added in Phase 3
    public VisibleBuildingDto {
        buildingId = ProtocolValidation.id(buildingId, "buildingId");
        buildingType = ProtocolValidation.text(buildingType, "buildingType", 32);
        ownerPlayerId = ProtocolValidation.id(ownerPlayerId, "ownerPlayerId");
        status = ProtocolValidation.text(status, "status", 32);
    }
}
