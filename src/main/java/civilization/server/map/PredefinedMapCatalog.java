package civilization.server.map;

import civilization.model.map.HexCoord;
import civilization.protocol.MapSummaryDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// PHASE 3 - Added in Phase 3
public final class PredefinedMapCatalog {
    // PHASE 3 - Added in Phase 3
    private final Map<String, PredefinedMapDefinition> maps;

    // PHASE 3 - Added in Phase 3
    public PredefinedMapCatalog(List<PredefinedMapDefinition> definitions) {
        LinkedHashMap<String, PredefinedMapDefinition> indexed = new LinkedHashMap<>();
        // PHASE 3 - Added in Phase 3
        for (PredefinedMapDefinition definition : definitions) {
            if (indexed.putIfAbsent(definition.id(), definition) != null) throw new IllegalArgumentException("duplicate map id");
        }
        if (indexed.size() < 3) throw new IllegalArgumentException("at least three predefined maps are required");
        maps = Map.copyOf(indexed);
    }

    // PHASE 3 - Added in Phase 3
    public static PredefinedMapCatalog standard() {
        return new PredefinedMapCatalog(List.of(
                map("emerald-plains", "Emerald Plains", 4, PredefinedMapStyle.EMERALD_PLAINS),
                map("twin-rivers", "Twin Rivers", 4, PredefinedMapStyle.TWIN_RIVERS),
                map("iron-crown", "Iron Crown", 6, PredefinedMapStyle.IRON_CROWN)));
    }

    // PHASE 3 - Added in Phase 3
    private static PredefinedMapDefinition map(String id, String name, int maximum,
                                                PredefinedMapStyle style) {
        LinkedHashMap<Integer, List<SpawnSlot>> layouts = new LinkedHashMap<>();
        layouts.put(2, slots(c(8, 0), c(-8, 0)));
        layouts.put(3, slots(c(8, 0), c(-4, 8), c(-4, -4)));
        layouts.put(4, slots(c(8, 0), c(0, 8), c(-8, 0), c(0, -8)));
        // PHASE 3 - Added in Phase 3
        if (maximum == 6) {
            layouts.put(5, slots(c(8, 0), c(8, -8), c(0, -8), c(-8, 0), c(-8, 8)));
            layouts.put(6, slots(c(8, 0), c(8, -8), c(0, -8), c(-8, 0), c(-8, 8), c(0, 8)));
        }
        return new PredefinedMapDefinition(id, name, 12, 2, maximum, layouts,
                3, 3, 3, 3, style);
    }

    // PHASE 3 - Added in Phase 3
    private static List<SpawnSlot> slots(HexCoord... coordinates) {
        java.util.ArrayList<SpawnSlot> slots = new java.util.ArrayList<>();
        // PHASE 3 - Added in Phase 3
        for (int index = 0; index < coordinates.length; index++) {
            slots.add(new SpawnSlot(index, coordinates[index]));
        }
        return List.copyOf(slots);
    }

    // PHASE 3 - Added in Phase 3
    private static HexCoord c(int q, int r) { return new HexCoord(q, r); }

    // PHASE 3 - Added in Phase 3
    public PredefinedMapDefinition require(String id) {
        PredefinedMapDefinition definition = maps.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown predefined map: " + id);
        return definition;
    }

    // PHASE 3 - Added in Phase 3
    public List<MapSummaryDto> summaries() {
        return maps.values().stream().map(PredefinedMapDefinition::summary).sorted(java.util.Comparator.comparing(MapSummaryDto::mapId)).toList();
    }
}
