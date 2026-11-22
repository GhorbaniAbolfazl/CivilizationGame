package civilization.server.map;

import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexTile;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.ResourceDeposit;
import civilization.model.map.TerrainType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// PHASE 3 - Added in Phase 3
public final class PredefinedMapLoader {
    // PHASE 3 - Added in Phase 3
    private static final int STANDARD_DEPOSIT = 100;

    // PHASE 3 - Added in Phase 3
    public GameMap load(PredefinedMapDefinition definition) {
        List<HexCoord> coordinates = coordinates(definition.radius());
        Map<HexCoord, HexTile> tiles = new HashMap<>();
        // PHASE 3 - Added in Phase 3
        for (HexCoord coordinate : coordinates) {
            tiles.put(coordinate, tile(coordinate, terrainFor(definition.style(), coordinate)));
        }
        Set<HexCoord> spawnCoordinates = allSpawnCoordinates(definition);
        clearSpawnRegions(definition.radius(), spawnCoordinates, tiles);
        installBalancedResourceSites(definition.radius(), spawnCoordinates, tiles);
        GameMap loaded = new GameMap(coordinates.stream().map(tiles::get).toList());
        installRivers(definition.style(), loaded);
        new PredefinedMapValidator().validateOrThrow(definition, loaded);
        return loaded;
    }

    // PHASE 3 - Added in Phase 3
    private static List<HexCoord> coordinates(int radius) {
        ArrayList<HexCoord> result = new ArrayList<>(1 + 3 * radius * (radius + 1));
        // PHASE 3 - Added in Phase 3
        for (int q = -radius; q <= radius; q++) {
            int minimumR = Math.max(-radius, -q - radius);
            int maximumR = Math.min(radius, -q + radius);
            for (int r = minimumR; r <= maximumR; r++) result.add(new HexCoord(q, r));
        }
        return List.copyOf(result);
    }

    // PHASE 3 - Added in Phase 3
    private static TerrainType terrainFor(PredefinedMapStyle style, HexCoord coordinate) {
        int q = coordinate.q(); int r = coordinate.r();
        int distance = coordinate.distanceTo(new HexCoord(0, 0));
        return switch (style) {
            case EMERALD_PLAINS -> {
                if (q >= 11 || q <= -12) yield TerrainType.SEA;
                if (q + r == 0 && (between(q, -10, -7) || between(q, -5, -1)
                        || between(q, 2, 5) || between(q, 8, 10))) yield TerrainType.MOUNTAIN_RANGE;
                if ((q <= -5 && between(r, -3, 3)) || (q >= 4 && between(r, -9, -6))) yield TerrainType.FOREST;
                if ((q + r == 1 || q + r == -1) && Math.abs(q) >= 5) yield TerrainType.MOUNTAIN;
                yield Math.floorMod(q - r, 7) == 0 ? TerrainType.GRASSLAND : TerrainType.PLAINS;
            }
            case TWIN_RIVERS -> {
                if (q + r >= 11 || q + r <= -12) yield TerrainType.SEA;
                if ((q == -7 || q == 7) && Math.abs(r) <= 7 && r != 0 && r != 1) yield TerrainType.MOUNTAIN_RANGE;
                if ((q == -6 || q == 6) && between(r, -6, 6)) yield TerrainType.MOUNTAIN;
                if ((between(q, -2, 1) && r <= -4) || (between(q, -1, 2) && r >= 4)) yield TerrainType.FOREST;
                yield Math.floorMod(q + 2 * r, 6) == 0 ? TerrainType.GRASSLAND : TerrainType.PLAINS;
            }
            case IRON_CROWN -> {
                if (q >= 11 || q <= -11) yield TerrainType.SEA;
                if (distance == 5 && !crownGate(coordinate)) yield TerrainType.MOUNTAIN_RANGE;
                if ((distance == 6 || distance == 7) && Math.floorMod(q - r, 3) != 0) yield TerrainType.MOUNTAIN;
                if (distance >= 8 && Math.floorMod(q + 2 * r, 4) <= 1) yield TerrainType.FOREST;
                yield Math.floorMod(2 * q - r, 6) == 0 ? TerrainType.GRASSLAND : TerrainType.PLAINS;
            }
        };
    }

    // PHASE 3 - Added in Phase 3
    private static Set<HexCoord> allSpawnCoordinates(PredefinedMapDefinition definition) {
        LinkedHashSet<HexCoord> result = new LinkedHashSet<>();
        // PHASE 3 - Added in Phase 3
        for (List<SpawnSlot> layout : definition.spawnLayouts().values()) {
            layout.stream().map(SpawnSlot::coordinate).forEach(result::add);
        }
        return Set.copyOf(result);
    }

    // PHASE 3 - Added in Phase 3
    private static void clearSpawnRegions(int radius, Set<HexCoord> spawns,
                                          Map<HexCoord, HexTile> tiles) {
        // PHASE 3 - Added in Phase 3
        for (HexCoord spawn : spawns) {
            tiles.put(spawn, tile(spawn, TerrainType.GRASSLAND));
            // PHASE 3 - Added in Phase 3
            for (HexCoord neighbor : spawn.neighbors()) {
                if (neighbor.distanceTo(new HexCoord(0, 0)) <= radius) {
                    tiles.put(neighbor, tile(neighbor, TerrainType.PLAINS));
                }
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void installBalancedResourceSites(int radius, Set<HexCoord> spawns,
                                                      Map<HexCoord, HexTile> tiles) {
        Set<HexCoord> unavailable = new HashSet<>(spawns);
        spawns.forEach(spawn -> unavailable.addAll(spawn.neighbors()));
        List<HexCoord> orderedSpawns = spawns.stream()
                .sorted(Comparator.comparingInt(HexCoord::q).thenComparingInt(HexCoord::r)).toList();
        // PHASE 3 - Added in Phase 3
        for (HexCoord spawn : orderedSpawns) {
            List<HexCoord> candidates = coordinates(radius).stream()
                    .filter(candidate -> candidate.distanceTo(spawn) >= 2
                            && candidate.distanceTo(spawn) <= 3)
                    .filter(candidate -> !unavailable.contains(candidate))
                    .sorted(Comparator.comparingInt((HexCoord value) -> value.distanceTo(spawn))
                            .thenComparingInt(HexCoord::q).thenComparingInt(HexCoord::r)).toList();
            if (candidates.size() < 3) throw new IllegalStateException(
                    "Predefined resource ring is too crowded around " + spawn);
            HexCoord food = candidates.get(0); HexCoord wood = candidates.get(1);
            HexCoord minerals = candidates.get(2);
            tiles.put(food, new HexTile(food, TerrainType.GRASSLAND,
                    new ResourceDeposit(NaturalResourceType.WHEAT, STANDARD_DEPOSIT)));
            tiles.put(wood, new HexTile(wood, TerrainType.FOREST,
                    new ResourceDeposit(NaturalResourceType.WOOD, STANDARD_DEPOSIT)));
            tiles.put(minerals, new HexTile(minerals, TerrainType.MOUNTAIN, List.of(
                    new ResourceDeposit(NaturalResourceType.STONE, STANDARD_DEPOSIT),
                    new ResourceDeposit(NaturalResourceType.IRON, STANDARD_DEPOSIT))));
            unavailable.add(food); unavailable.add(wood); unavailable.add(minerals);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void installRivers(PredefinedMapStyle style, GameMap map) {
        switch (style) {
            case EMERALD_PLAINS -> addRiverLine(map, -9, 9, q -> new HexCoord(q, 3));
            case TWIN_RIVERS -> {
                addRiverLine(map, -9, 9, r -> new HexCoord(-3, r));
                addRiverLine(map, -9, 9, r -> new HexCoord(3, r));
            }
            case IRON_CROWN -> {
                addRiverLine(map, -10, 10, r -> new HexCoord(0, r));
                addRiverLine(map, -9, 9, q -> new HexCoord(q, 0));
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void addRiverLine(GameMap map, int start, int end,
                                     java.util.function.IntFunction<HexCoord> coordinate) {
        HexCoord previous = null;
        // PHASE 3 - Added in Phase 3
        for (int value = start; value <= end; value++) {
            HexCoord current = coordinate.apply(value);
            if (previous != null && traversable(map, previous) && traversable(map, current)
                    && previous.isAdjacentTo(current)) map.addRiver(previous, current);
            previous = current;
        }
    }

    // PHASE 3 - Added in Phase 3
    private static boolean traversable(GameMap map, HexCoord coordinate) {
        if (!map.contains(coordinate)) return false;
        TerrainType terrain = map.requireTile(coordinate).terrain();
        return terrain != TerrainType.SEA && terrain != TerrainType.MOUNTAIN_RANGE;
    }

    // PHASE 3 - Added in Phase 3
    private static HexTile tile(HexCoord coordinate, TerrainType terrain) {
        return switch (terrain) {
            case FOREST -> new HexTile(coordinate, terrain,
                    new ResourceDeposit(NaturalResourceType.WOOD, STANDARD_DEPOSIT));
            case MOUNTAIN -> new HexTile(coordinate, terrain,
                    new ResourceDeposit(NaturalResourceType.STONE, STANDARD_DEPOSIT));
            case SEA -> new HexTile(coordinate, terrain,
                    new ResourceDeposit(NaturalResourceType.FISH, STANDARD_DEPOSIT));
            default -> new HexTile(coordinate, terrain);
        };
    }

    // PHASE 3 - Added in Phase 3
    private static boolean crownGate(HexCoord coordinate) {
        return coordinate.q() == 0 || coordinate.r() == 0
                || coordinate.q() + coordinate.r() == 0;
    }

    // PHASE 3 - Added in Phase 3
    private static boolean between(int value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }
}
