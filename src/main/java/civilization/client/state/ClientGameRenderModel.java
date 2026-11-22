package civilization.client.state;

import civilization.protocol.GameSnapshotDto;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record ClientGameRenderModel(long stateVersion, GameSnapshotDto snapshot) {
    // PHASE 3 - Added in Phase 3
    public ClientGameRenderModel { if (stateVersion < 0) throw new IllegalArgumentException("stateVersion must be non-negative"); Objects.requireNonNull(snapshot); }
}
