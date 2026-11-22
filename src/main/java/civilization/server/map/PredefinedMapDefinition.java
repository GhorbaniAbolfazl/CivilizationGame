package civilization.server.map;

import civilization.protocol.MapSummaryDto;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// PHASE 3 - Added in Phase 3
public record PredefinedMapDefinition(String id, String displayName, int radius,
                                      int minimumPlayers, int maximumPlayers,
                                      Map<Integer, List<SpawnSlot>> spawnLayouts,
                                      int foodDistance, int woodDistance,
                                      int stoneDistance, int ironDistance,
                                      PredefinedMapStyle style) {
    // PHASE 3 - Added in Phase 3
    public PredefinedMapDefinition(String id, String displayName, int radius,
                                   int minimumPlayers, int maximumPlayers,
                                   List<SpawnSlot> spawnSlots, int foodDistance,
                                   int woodDistance, int stoneDistance, int ironDistance) {
        this(id, displayName, radius, minimumPlayers, maximumPlayers,
                legacyLayouts(minimumPlayers, maximumPlayers, spawnSlots),
                foodDistance, woodDistance, stoneDistance, ironDistance, styleFor(id));
    }
    // PHASE 3 - Added in Phase 3
    public PredefinedMapDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("map id is required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("map name is required");
        if (radius < 1 || minimumPlayers < 2 || maximumPlayers < minimumPlayers) throw new IllegalArgumentException("invalid map capacity");
        Objects.requireNonNull(spawnLayouts, "spawnLayouts");
        Objects.requireNonNull(style, "style");
        java.util.LinkedHashMap<Integer, List<SpawnSlot>> copiedLayouts = new java.util.LinkedHashMap<>();
        // PHASE 3 - Added in Phase 3
        for (int playerCount = minimumPlayers; playerCount <= maximumPlayers; playerCount++) {
            List<SpawnSlot> slots = List.copyOf(Objects.requireNonNull(
                    spawnLayouts.get(playerCount), "missing spawn layout for " + playerCount));
            validateSpawnLayout(slots, playerCount, radius);
            copiedLayouts.put(playerCount, slots);
        }
        spawnLayouts = Map.copyOf(copiedLayouts);
        // PHASE 3 - Added in Phase 3
        if (foodDistance < 0 || woodDistance < 0 || stoneDistance < 0 || ironDistance < 0) {
            throw new IllegalArgumentException("resource distances must be non-negative");
        }
        // PHASE 3 - Added in Phase 3
        if (foodDistance > radius || woodDistance > radius || stoneDistance > radius || ironDistance > radius) {
            throw new IllegalArgumentException("every spawn must have access to each mandatory resource");
        }
    }

    // PHASE 3 - Added in Phase 3
    public boolean supports(int playerCount) {
        return playerCount >= minimumPlayers && playerCount <= maximumPlayers;
    }

    // PHASE 3 - Added in Phase 3
    public List<SpawnSlot> spawnSlotsFor(int playerCount) {
        if (!supports(playerCount)) throw new IllegalArgumentException("unsupported player count");
        return spawnLayouts.get(playerCount);
    }

    // PHASE 3 - Added in Phase 3
    public List<SpawnSlot> spawnSlots() { return spawnSlotsFor(maximumPlayers); }

    // PHASE 3 - Added in Phase 3
    public MapSummaryDto summary() {
        return new MapSummaryDto(id, displayName, minimumPlayers, maximumPlayers);
    }

    // PHASE 3 - Added in Phase 3
    private static void validateSpawnLayout(List<SpawnSlot> slots, int playerCount, int radius) {
        if (slots.size() != playerCount) throw new IllegalArgumentException(
                "spawn layout must contain exactly " + playerCount + " slots");
        long distinct = slots.stream().map(SpawnSlot::coordinate).distinct().count();
        if (distinct != slots.size()) throw new IllegalArgumentException("spawn locations must be unique");
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index).index() != index) throw new IllegalArgumentException("spawn indices must be ordered");
            if (slots.get(index).coordinate().distanceTo(new civilization.model.map.HexCoord(0, 0)) > radius) {
                throw new IllegalArgumentException("spawn location lies outside the map");
            }
        }
        // PHASE 3 - Added in Phase 3
        int minimumNearest = Integer.MAX_VALUE;
        int maximumNearest = 0;
        for (int left = 0; left < slots.size(); left++) {
            int nearest = Integer.MAX_VALUE;
            for (int right = 0; right < slots.size(); right++) {
                if (left != right) nearest = Math.min(nearest, slots.get(left).coordinate()
                        .distanceTo(slots.get(right).coordinate()));
            }
            minimumNearest = Math.min(minimumNearest, nearest);
            maximumNearest = Math.max(maximumNearest, nearest);
        }
        if (minimumNearest < radius / 2 || maximumNearest - minimumNearest > 1) {
            throw new IllegalArgumentException("spawn locations are not fairly separated");
        }
    }

    // PHASE 3 - Added in Phase 3
    private static Map<Integer, List<SpawnSlot>> legacyLayouts(int minimumPlayers,
                                                               int maximumPlayers,
                                                               List<SpawnSlot> slots) {
        Objects.requireNonNull(slots, "spawnSlots");
        if (slots.size() < maximumPlayers) throw new IllegalArgumentException("not enough spawn slots");
        java.util.LinkedHashMap<Integer, List<SpawnSlot>> result = new java.util.LinkedHashMap<>();
        // PHASE 3 - Added in Phase 3
        for (int count = minimumPlayers; count <= maximumPlayers; count++) {
            java.util.ArrayList<SpawnSlot> layout = new java.util.ArrayList<>();
            for (int index = 0; index < count; index++) {
                layout.add(new SpawnSlot(index, slots.get(index).coordinate()));
            }
            result.put(count, List.copyOf(layout));
        }
        return Map.copyOf(result);
    }

    // PHASE 3 - Added in Phase 3
    private static PredefinedMapStyle styleFor(String id) {
        return switch (Objects.requireNonNull(id, "id")) {
            case "twin-rivers" -> PredefinedMapStyle.TWIN_RIVERS;
            case "iron-crown" -> PredefinedMapStyle.IRON_CROWN;
            default -> PredefinedMapStyle.EMERALD_PLAINS;
        };
    }
}
