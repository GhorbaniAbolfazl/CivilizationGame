package civilization.server.map;

import civilization.model.economy.ResourceType;
import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexTile;
import civilization.model.map.TerrainType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// PHASE 3 - Added in Phase 3
public final class PredefinedMapValidator {
    // PHASE 3 - Added in Phase 3
    public PredefinedMapValidationReport validateOrThrow(PredefinedMapDefinition definition,
                                                          GameMap map) {
        int expectedTiles = 1 + 3 * definition.radius() * (definition.radius() + 1);
        require(map.size() == expectedTiles, "map must fill its declared hex radius");
        int traversable = count(map, this::traversable);
        int sea = count(map, tile -> tile.terrain() == TerrainType.SEA);
        int mountains = count(map, tile -> tile.terrain() == TerrainType.MOUNTAIN);
        int ranges = count(map, tile -> tile.terrain() == TerrainType.MOUNTAIN_RANGE);
        require(traversable >= definition.maximumPlayers() * 50,
                "map is too small for expansion and separate territories");
        require(sea >= definition.radius(), "sea placement is too small to form a strategic coast");
        require(mountains >= definition.radius(), "map needs strategic mineable mountain regions");
        require(ranges >= definition.radius(), "mountain ranges are not substantial");
        require(largestTerrainComponent(map, TerrainType.SEA) >= 8,
                "sea tiles must form a logical contiguous coastline");
        require(largestTerrainComponent(map, TerrainType.MOUNTAIN_RANGE) >= 4,
                "mountain-range tiles must form logical ridges");
        require(map.riverEdges().size() >= 8, "river placement must form useful routes and borders");
        require(largestRiverComponent(map) >= 8,
                "river edges must form a logical continuous watercourse");
        LinkedHashMap<Integer, Integer> spacing = new LinkedHashMap<>();
        LinkedHashMap<Integer, Map<ResourceType, Integer>> access = new LinkedHashMap<>();
        // PHASE 3 - Added in Phase 3
        for (int players = definition.minimumPlayers(); players <= definition.maximumPlayers(); players++) {
            List<SpawnSlot> layout = definition.spawnSlotsFor(players);
            int minimumSpacing = minimumSpacing(layout);
            spacing.put(players, minimumSpacing);
            require(minimumSpacing >= definition.radius() / 2,
                    "spawn spacing is below the fair minimum for " + players + " players");
            Map<ResourceType, Integer> maximumDistances = validateResourceAccess(
                    definition, map, layout);
            access.put(players, maximumDistances);
            validateConnectedTradePaths(map, layout);
            // PHASE 3 - Added in Phase 3
            for (SpawnSlot slot : layout) {
                long expansionTiles = reachableWithin(map, slot.coordinate(), 4).size();
                require(expansionTiles >= 25,
                        "spawn lacks room for a defensible region and second Hall Town: "
                                + slot.coordinate());
                require(hasDefensiveFeatureWithin(map, slot.coordinate(), 5),
                        "spawn lacks a nearby coast, ridge, river, or map-edge defensive anchor: "
                                + slot.coordinate());
            }
        }
        return new PredefinedMapValidationReport(definition.id(), map.size(), traversable,
                sea, mountains, ranges, map.riverEdges().size(), Map.copyOf(spacing),
                Map.copyOf(access));
    }

    // PHASE 3 - Added in Phase 3
    private Map<ResourceType, Integer> validateResourceAccess(PredefinedMapDefinition definition,
                                                               GameMap map,
                                                               List<SpawnSlot> layout) {
        EnumMap<ResourceType, List<Integer>> distances = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) distances.put(type, new ArrayList<>());
        // PHASE 3 - Added in Phase 3
        for (SpawnSlot slot : layout) {
            Map<HexCoord, Integer> paths = pathDistances(map, slot.coordinate());
            // PHASE 3 - Added in Phase 3
            for (ResourceType type : ResourceType.values()) {
                int nearest = map.tiles().stream().filter(tile -> hasResource(tile, type))
                        .mapToInt(tile -> paths.getOrDefault(tile.coordinate(), Integer.MAX_VALUE))
                        .min().orElse(Integer.MAX_VALUE);
                require(nearest != Integer.MAX_VALUE, "spawn cannot reach " + type);
                distances.get(type).add(nearest);
            }
        }
        EnumMap<ResourceType, Integer> maxima = new EnumMap<>(ResourceType.class);
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) {
            int minimum = distances.get(type).stream().mapToInt(Integer::intValue).min().orElseThrow();
            int maximum = distances.get(type).stream().mapToInt(Integer::intValue).max().orElseThrow();
            require(maximum - minimum <= 1, type + " access is not comparable between spawns");
            require(maximum <= declaredDistance(definition, type),
                    type + " lies beyond the declared fair-access distance");
            maxima.put(type, maximum);
        }
        return Map.copyOf(maxima);
    }

    // PHASE 3 - Added in Phase 3
    private void validateConnectedTradePaths(GameMap map, List<SpawnSlot> layout) {
        Map<HexCoord, Integer> reachable = pathDistances(map, layout.get(0).coordinate());
        // PHASE 3 - Added in Phase 3
        for (SpawnSlot slot : layout) {
            require(reachable.containsKey(slot.coordinate()),
                    "impassable terrain disconnects a spawn from natural trade paths");
        }
    }

    // PHASE 3 - Added in Phase 3
    private Map<HexCoord, Integer> pathDistances(GameMap map, HexCoord start) {
        require(map.contains(start) && traversable(map.requireTile(start)),
                "spawn must be on traversable terrain");
        HashMap<HexCoord, Integer> distance = new HashMap<>();
        ArrayDeque<HexCoord> open = new ArrayDeque<>();
        distance.put(start, 0); open.add(start);
        // PHASE 3 - Added in Phase 3
        while (!open.isEmpty()) {
            HexCoord current = open.removeFirst();
            int nextDistance = distance.get(current) + 1;
            // PHASE 3 - Added in Phase 3
            for (HexTile neighbor : map.neighborsOf(current)) {
                if (traversable(neighbor)
                        && distance.putIfAbsent(neighbor.coordinate(), nextDistance) == null) {
                    open.addLast(neighbor.coordinate());
                }
            }
        }
        return distance;
    }

    // PHASE 3 - Added in Phase 3
    private Set<HexCoord> reachableWithin(GameMap map, HexCoord start, int maximumDistance) {
        Map<HexCoord, Integer> distances = pathDistances(map, start);
        HashSet<HexCoord> result = new HashSet<>();
        distances.forEach((coordinate, distance) -> {
            if (distance <= maximumDistance) result.add(coordinate);
        });
        return Set.copyOf(result);
    }

    // PHASE 3 - Added in Phase 3
    private int largestTerrainComponent(GameMap map, TerrainType terrain) {
        HashSet<HexCoord> remaining = new HashSet<>();
        map.tiles().stream().filter(tile -> tile.terrain() == terrain)
                .map(HexTile::coordinate).forEach(remaining::add);
        int largest = 0;
        // PHASE 3 - Added in Phase 3
        while (!remaining.isEmpty()) {
            HexCoord start = remaining.iterator().next();
            ArrayDeque<HexCoord> open = new ArrayDeque<>();
            open.add(start); remaining.remove(start); int size = 0;
            // PHASE 3 - Added in Phase 3
            while (!open.isEmpty()) {
                HexCoord current = open.removeFirst(); size++;
                // PHASE 3 - Added in Phase 3
                for (HexCoord neighbor : current.neighbors()) {
                    if (remaining.remove(neighbor)) open.addLast(neighbor);
                }
            }
            largest = Math.max(largest, size);
        }
        return largest;
    }

    // PHASE 3 - Added in Phase 3
    private int largestRiverComponent(GameMap map) {
        HashMap<HexCoord, Set<HexCoord>> graph = new HashMap<>();
        // PHASE 3 - Added in Phase 3
        for (var edge : map.riverEdges()) {
            graph.computeIfAbsent(edge.first(), ignored -> new HashSet<>()).add(edge.second());
            graph.computeIfAbsent(edge.second(), ignored -> new HashSet<>()).add(edge.first());
        }
        HashSet<HexCoord> remaining = new HashSet<>(graph.keySet()); int largestEdges = 0;
        // PHASE 3 - Added in Phase 3
        while (!remaining.isEmpty()) {
            HexCoord start = remaining.iterator().next(); ArrayDeque<HexCoord> open = new ArrayDeque<>();
            open.add(start); remaining.remove(start); int vertices = 0;
            // PHASE 3 - Added in Phase 3
            while (!open.isEmpty()) {
                HexCoord current = open.removeFirst(); vertices++;
                for (HexCoord neighbor : graph.getOrDefault(current, Set.of())) {
                    if (remaining.remove(neighbor)) open.addLast(neighbor);
                }
            }
            largestEdges = Math.max(largestEdges, Math.max(0, vertices - 1));
        }
        return largestEdges;
    }

    // PHASE 3 - Added in Phase 3
    private boolean hasDefensiveFeatureWithin(GameMap map, HexCoord spawn, int radius) {
        return map.coordinatesWithin(spawn, radius).stream().anyMatch(coordinate -> {
            HexTile tile = map.requireTile(coordinate);
            return tile.terrain() == TerrainType.SEA
                    || tile.terrain() == TerrainType.MOUNTAIN_RANGE
                    || map.neighborsOf(coordinate).size() < 6
                    || map.riverEdges().stream().anyMatch(edge -> edge.touches(coordinate));
        });
    }

    // PHASE 3 - Added in Phase 3
    private static int minimumSpacing(List<SpawnSlot> layout) {
        int minimum = Integer.MAX_VALUE;
        // PHASE 3 - Added in Phase 3
        for (int left = 0; left < layout.size(); left++) {
            for (int right = left + 1; right < layout.size(); right++) {
                minimum = Math.min(minimum, layout.get(left).coordinate()
                        .distanceTo(layout.get(right).coordinate()));
            }
        }
        return minimum;
    }

    // PHASE 3 - Added in Phase 3
    private static int count(GameMap map, java.util.function.Predicate<HexTile> predicate) {
        return (int) map.tiles().stream().filter(predicate).count();
    }

    // PHASE 3 - Added in Phase 3
    private static boolean hasResource(HexTile tile, ResourceType type) {
        return tile.resourceDeposits().keySet().stream()
                .filter(civilization.model.map.NaturalResourceType::isStoredResource)
                .anyMatch(resource -> resource.storedAs() == type);
    }

    // PHASE 3 - Added in Phase 3
    private static int declaredDistance(PredefinedMapDefinition definition, ResourceType type) {
        return switch (type) {
            case FOOD -> definition.foodDistance();
            case WOOD -> definition.woodDistance();
            case STONE -> definition.stoneDistance();
            case IRON -> definition.ironDistance();
        };
    }

    // PHASE 3 - Added in Phase 3
    private boolean traversable(HexTile tile) {
        return tile.terrain() != TerrainType.SEA && tile.terrain() != TerrainType.MOUNTAIN_RANGE;
    }

    // PHASE 3 - Added in Phase 3
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException("Invalid predefined map: " + message);
    }
}
