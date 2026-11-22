package civilization.server.lobby;

import civilization.server.map.SpawnSlot;
import civilization.server.session.PlayerId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record InitialPlayerSetup(PlayerId playerId, SpawnSlot spawn, String hallId,
                                 List<String> unitIds, Map<String, Long> resources) {
    // PHASE 3 - Added in Phase 3
    public InitialPlayerSetup { Objects.requireNonNull(playerId); Objects.requireNonNull(spawn); Objects.requireNonNull(hallId); unitIds = List.copyOf(unitIds); resources = Map.copyOf(resources); }
}
