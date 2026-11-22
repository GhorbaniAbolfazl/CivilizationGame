package civilization.phase3;

import civilization.model.map.GameMap;
import civilization.model.map.HexTile;
import civilization.server.map.PredefinedMapCatalog;
import civilization.server.map.PredefinedMapDefinition;
import civilization.server.map.PredefinedMapLoader;
import civilization.server.map.PredefinedMapValidationReport;
import civilization.server.map.PredefinedMapValidator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// PHASE 3 - Added in Phase 3
public final class Phase3PredefinedMapTestSuite {
    // PHASE 3 - Added in Phase 3
    private static final List<String> MAP_IDS = List.of(
            "emerald-plains", "twin-rivers", "iron-crown");

    // PHASE 3 - Added in Phase 3
    private Phase3PredefinedMapTestSuite() { }

    // PHASE 3 - Added in Phase 3
    public static void main(String[] args) {
        catalogContainsThreeDistinctPlayableBlueprints();
        everySupportedPlayerLayoutPassesFairnessAndAccessValidation();
        loadingIsDeterministicAndUsesDeclaredSpawnSlots();
        System.out.println("Phase3PredefinedMapTestSuite: all checks passed");
    }

    // PHASE 3 - Added in Phase 3
    private static void catalogContainsThreeDistinctPlayableBlueprints() {
        PredefinedMapCatalog catalog = PredefinedMapCatalog.standard();
        assert catalog.summaries().size() >= 3;
        Set<String> signatures = new HashSet<>();
        // PHASE 3 - Added in Phase 3
        for (String id : MAP_IDS) {
            PredefinedMapDefinition definition = catalog.require(id);
            GameMap map = new PredefinedMapLoader().load(definition);
            String signature = map.tiles().stream().map(tile -> tile.coordinate() + "=" + tile.terrain())
                    .sorted().reduce("", (left, right) -> left + ";" + right);
            assert signatures.add(signature) : "maps must have distinct designed terrain";
            assert map.size() == 469 : id;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void everySupportedPlayerLayoutPassesFairnessAndAccessValidation() {
        PredefinedMapCatalog catalog = PredefinedMapCatalog.standard();
        // PHASE 3 - Added in Phase 3
        for (String id : MAP_IDS) {
            PredefinedMapDefinition definition = catalog.require(id);
            GameMap map = new PredefinedMapLoader().load(definition);
            PredefinedMapValidationReport report = new PredefinedMapValidator()
                    .validateOrThrow(definition, map);
            assert report.traversableTileCount() >= definition.maximumPlayers() * 50;
            assert report.seaTileCount() >= 12;
            assert report.mountainTileCount() >= 12;
            assert report.mountainRangeTileCount() >= 12;
            assert report.riverEdgeCount() >= 8;
            // PHASE 3 - Added in Phase 3
            for (int players = definition.minimumPlayers();
                 players <= definition.maximumPlayers(); players++) {
                assert definition.spawnSlotsFor(players).size() == players;
                assert report.minimumSpawnSpacing().get(players) >= 6;
                assert report.maximumResourceDistances().get(players).values().stream()
                        .allMatch(distance -> distance <= 3);
            }
            System.out.println("MAP " + report);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void loadingIsDeterministicAndUsesDeclaredSpawnSlots() {
        PredefinedMapCatalog catalog = PredefinedMapCatalog.standard();
        PredefinedMapLoader loader = new PredefinedMapLoader();
        // PHASE 3 - Added in Phase 3
        for (String id : MAP_IDS) {
            PredefinedMapDefinition definition = catalog.require(id);
            GameMap first = loader.load(definition); GameMap second = loader.load(definition);
            assert first.riverEdges().equals(second.riverEdges());
            // PHASE 3 - Added in Phase 3
            for (HexTile tile : first.tiles()) {
                HexTile copy = second.requireTile(tile.coordinate());
                assert tile.terrain() == copy.terrain();
                assert tile.resourceDeposits().keySet().equals(copy.resourceDeposits().keySet());
            }
            // PHASE 3 - Added in Phase 3
            for (List<civilization.server.map.SpawnSlot> layout : definition.spawnLayouts().values()) {
                layout.forEach(slot -> assertTraversableSpawn(first, slot.coordinate()));
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void assertTraversableSpawn(GameMap map,
                                                civilization.model.map.HexCoord coordinate) {
        var terrain = map.requireTile(coordinate).terrain();
        assert terrain != civilization.model.map.TerrainType.SEA;
        assert terrain != civilization.model.map.TerrainType.MOUNTAIN_RANGE;
    }
}
