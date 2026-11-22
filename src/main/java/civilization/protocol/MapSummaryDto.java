package civilization.protocol;

// PHASE 3 - Added in Phase 3
public record MapSummaryDto(
        // PHASE 3 - Added in Phase 3
        String mapId,
        // PHASE 3 - Added in Phase 3
        String displayName,
        // PHASE 3 - Added in Phase 3
        int minimumPlayers,
        // PHASE 3 - Added in Phase 3
        int maximumPlayers) {
    // PHASE 3 - Added in Phase 3
    public MapSummaryDto {
        mapId = ProtocolValidation.id(mapId, "mapId");
        displayName = ProtocolValidation.text(displayName, "displayName", 128);
        ProtocolValidation.positive(minimumPlayers, "minimumPlayers");
        if (maximumPlayers < minimumPlayers) {
            throw new IllegalArgumentException("maximumPlayers must include minimumPlayers");
        }
    }

    // PHASE 3 - Added in Phase 3
    @Override public String toString() { return displayName + " (" + minimumPlayers + "-" + maximumPlayers + ")"; }
}
