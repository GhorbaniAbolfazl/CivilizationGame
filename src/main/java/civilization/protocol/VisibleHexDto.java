package civilization.protocol;

import java.util.Map;

// PHASE 3 - Added in Phase 3
public record VisibleHexDto(int q, int r, boolean currentlyVisible, boolean owned,
                            String terrain, Map<String, Integer> resources) {
    // PHASE 3 - Added in Phase 3
    public VisibleHexDto {
        terrain = ProtocolValidation.text(terrain, "terrain", 32);
        resources = ProtocolValidation.map(resources, "resources");
        // PHASE 3 - Added in Phase 3
        if (resources.values().stream().anyMatch(amount -> amount < 0)) throw new IllegalArgumentException("resource amounts must be non-negative");
    }
}
