package civilization.protocol;

import java.util.List;
import java.time.Instant;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record LobbySnapshotDto(
        // PHASE 3 - Added in Phase 3
        String lobbyId,
        // PHASE 3 - Added in Phase 3
        String hostPlayerId,
        // PHASE 3 - Added in Phase 3
        String selectedMapId,
        // PHASE 3 - Added in Phase 3
        String lifecycle,
        // PHASE 3 - Added in Phase 3
        Instant createdAt,
        // PHASE 3 - Added in Phase 3
        List<LobbyMemberDto> members,
        // PHASE 3 - Added in Phase 3
        List<MapSummaryDto> availableMaps) {
    // PHASE 3 - Added in Phase 3
    public LobbySnapshotDto(String lobbyId, String hostPlayerId, String selectedMapId,
                            String lifecycle, List<LobbyMemberDto> members) {
        this(lobbyId, hostPlayerId, selectedMapId, lifecycle, Instant.EPOCH, members, List.of());
    }

    // PHASE 3 - Added in Phase 3
    public LobbySnapshotDto(String lobbyId, String hostPlayerId, String selectedMapId,
                            String lifecycle, List<LobbyMemberDto> members,
                            List<MapSummaryDto> availableMaps) {
        this(lobbyId, hostPlayerId, selectedMapId, lifecycle, Instant.EPOCH, members, availableMaps);
    }

    // PHASE 3 - Added in Phase 3
    public LobbySnapshotDto {
        lobbyId = ProtocolValidation.id(lobbyId, "lobbyId");
        // PHASE 3 - Added in Phase 3
        hostPlayerId = ProtocolValidation.optionalText(hostPlayerId, "hostPlayerId", 128);
        selectedMapId = ProtocolValidation.optionalText(selectedMapId, "selectedMapId", 128);
        lifecycle = ProtocolValidation.text(lifecycle, "lifecycle", 32);
        Objects.requireNonNull(createdAt, "createdAt");
        members = ProtocolValidation.list(members, "members");
        availableMaps = ProtocolValidation.list(availableMaps, "availableMaps");
    }
}
