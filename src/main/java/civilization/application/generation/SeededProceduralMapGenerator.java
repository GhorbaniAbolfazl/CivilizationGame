package civilization.application.generation;

import civilization.model.map.GameMap;
import civilization.model.map.HexCoord;
import civilization.model.map.HexTile;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.ResourceDeposit;
import civilization.model.map.TerrainType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Seeded generator built from contiguous ridge walks and overlapping forest clusters.
 * It intentionally avoids independent per-hex terrain rolls, which produce visual noise.
 */
public final class SeededProceduralMapGenerator implements MapGenerator {
    private static final List<HexCoord> DIRECTIONS = new HexCoord(0, 0).neighbors();

    @Override
    public GameMap generate(int radius, long seed, HexCoord townHallCoordinate) {
        if (radius < 4) {
            throw new IllegalArgumentException("Playable map radius must be at least four");
        }
        Objects.requireNonNull(townHallCoordinate, "townHallCoordinate");
        Set<HexCoord> coordinates = radiusCoordinates(radius, townHallCoordinate);
        SplittableRandom random = new SplittableRandom(seed);
        Map<HexCoord, TerrainType> terrain = baseTerrain(coordinates, seed);

        createCoastline(terrain, coordinates, townHallCoordinate, radius);
        createMountainRange(terrain, coordinates, townHallCoordinate, radius, random.split());
        createOrdinaryMountainClusters(terrain, coordinates, townHallCoordinate, seed);
        createForestClusters(terrain, coordinates, townHallCoordinate, radius, random.split());
        removeIsolatedForests(terrain, coordinates);
        terrain.put(townHallCoordinate, TerrainType.GRASSLAND);
        guaranteeNearbyForest(terrain, coordinates, townHallCoordinate, seed);

        List<HexTile> tiles = new ArrayList<>(coordinates.size());
        coordinates.stream().sorted(java.util.Comparator
                .comparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                .forEach(coordinate -> tiles.add(createTile(
                        coordinate, terrain.get(coordinate), seed)));
        GameMap map = new GameMap(tiles);
        createRiverEdges(map, townHallCoordinate, radius, seed);
        validateGuarantees(map, townHallCoordinate, radius);
        return map;
    }

    private static Set<HexCoord> radiusCoordinates(int radius, HexCoord center) {
        Set<HexCoord> result = new HashSet<>();
        for (int q = -radius; q <= radius; q++) {
            int minR = Math.max(-radius, -q - radius);
            int maxR = Math.min(radius, -q + radius);
            for (int r = minR; r <= maxR; r++) {
                result.add(new HexCoord(center.q() + q, center.r() + r));
            }
        }
        return result;
    }

    private static Map<HexCoord, TerrainType> baseTerrain(
            Set<HexCoord> coordinates, long seed) {
        Map<HexCoord, TerrainType> terrain = new HashMap<>();
        for (HexCoord coordinate : coordinates) {
            long broadBand = mix(seed, coordinate.q() / 2, coordinate.r() / 2);
            terrain.put(coordinate, Math.floorMod(broadBand, 5) < 2
                    ? TerrainType.PLAINS : TerrainType.GRASSLAND);
        }
        return terrain;
    }

    private static void createMountainRange(
            Map<HexCoord, TerrainType> terrain,
            Set<HexCoord> coordinates,
            HexCoord center,
            int radius,
            SplittableRandom random) {
        int direction = random.nextInt(DIRECTIONS.size());
        HexCoord cursor = add(center, scale(DIRECTIONS.get(direction), Math.max(2, radius / 2)));
        if (!coordinates.contains(cursor)) {
            cursor = center.neighbors().stream().filter(coordinates::contains).findFirst().orElse(center);
        }
        int length = Math.max(6, radius + 3);
        for (int i = 0; i < length; i++) {
            if (!cursor.equals(center) && terrain.get(cursor) != TerrainType.SEA) {
                terrain.put(cursor, TerrainType.MOUNTAIN_RANGE);
            }
            int turn = random.nextInt(5) == 0 ? (random.nextBoolean() ? 1 : -1) : 0;
            direction = Math.floorMod(direction + turn, DIRECTIONS.size());
            HexCoord next = add(cursor, DIRECTIONS.get(direction));
            if (!coordinates.contains(next) || next.distanceTo(center) <= 1) {
                direction = Math.floorMod(direction + 2, DIRECTIONS.size());
                next = add(cursor, DIRECTIONS.get(direction));
            }
            if (coordinates.contains(next) && terrain.get(next) == TerrainType.SEA) {
                break;
            }
            if (coordinates.contains(next)) {
                cursor = next;
            }
        }
    }

    private static void createForestClusters(
            Map<HexCoord, TerrainType> terrain,
            Set<HexCoord> coordinates,
            HexCoord center,
            int radius,
            SplittableRandom random) {
        List<HexCoord> coordinateList = coordinates.stream()
                .filter(coordinate -> !coordinate.equals(center))
                .toList();
        int clusterCount = Math.max(3, radius / 2);
        for (int cluster = 0; cluster < clusterCount; cluster++) {
            HexCoord anchor = coordinateList.get(random.nextInt(coordinateList.size()));
            int clusterRadius = 1 + random.nextInt(2);
            for (HexCoord coordinate : coordinates) {
                if (coordinate.distanceTo(anchor) <= clusterRadius
                        && terrain.get(coordinate) != TerrainType.MOUNTAIN
                        && terrain.get(coordinate) != TerrainType.MOUNTAIN_RANGE
                        && terrain.get(coordinate) != TerrainType.SEA
                        && !coordinate.equals(center)) {
                    terrain.put(coordinate, TerrainType.FOREST);
                }
            }
        }
    }

    private static void guaranteeNearbyForest(
            Map<HexCoord, TerrainType> terrain,
            Set<HexCoord> coordinates,
            HexCoord center,
            long seed) {
        boolean nearby = coordinates.stream().anyMatch(coordinate ->
                coordinate.distanceTo(center) <= 2 && terrain.get(coordinate) == TerrainType.FOREST);
        if (nearby) {
            return;
        }
        int direction = Math.floorMod(seed, DIRECTIONS.size());
        HexCoord first = java.util.stream.IntStream.range(0, DIRECTIONS.size())
                .mapToObj(offset -> add(center, DIRECTIONS.get(
                        Math.floorMod(direction + offset, DIRECTIONS.size()))))
                .filter(coordinates::contains)
                .filter(candidate -> terrain.get(candidate) != TerrainType.MOUNTAIN)
                .filter(candidate -> terrain.get(candidate) != TerrainType.MOUNTAIN_RANGE)
                .filter(candidate -> terrain.get(candidate) != TerrainType.SEA)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "No non-mountain hex is available for the nearby forest guarantee"));
        if (coordinates.contains(first)) {
            terrain.put(first, TerrainType.FOREST);
            HexCoord clusterNeighbor = first.neighbors().stream()
                    .filter(coordinates::contains)
                    .filter(neighbor -> !neighbor.equals(center))
                    .filter(neighbor -> terrain.get(neighbor) != TerrainType.MOUNTAIN)
                    .filter(neighbor -> terrain.get(neighbor) != TerrainType.MOUNTAIN_RANGE)
                    .filter(neighbor -> terrain.get(neighbor) != TerrainType.SEA)
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "No second hex is available for the nearby forest cluster"));
            terrain.put(clusterNeighbor, TerrainType.FOREST);
        }
    }

    private static void removeIsolatedForests(
            Map<HexCoord, TerrainType> terrain,
            Set<HexCoord> coordinates) {
        Set<HexCoord> isolated = new HashSet<>();
        for (HexCoord coordinate : coordinates) {
            if (terrain.get(coordinate) == TerrainType.FOREST
                    && coordinate.neighbors().stream().noneMatch(neighbor ->
                    coordinates.contains(neighbor) && terrain.get(neighbor) == TerrainType.FOREST)) {
                isolated.add(coordinate);
            }
        }
        isolated.forEach(coordinate -> terrain.put(coordinate,
                Math.floorMod(coordinate.q() + coordinate.r(), 3) == 0
                        ? TerrainType.PLAINS : TerrainType.GRASSLAND));
    }

    /** A single connected outer sea ring produces a coherent coast instead of noisy water tiles. */
    private static void createCoastline(Map<HexCoord, TerrainType> terrain,
            Set<HexCoord> coordinates, HexCoord center, int radius) {
        coordinates.stream().filter(coord -> coord.distanceTo(center) == radius)
                .forEach(coord -> terrain.put(coord, TerrainType.SEA));
    }

    private static void createOrdinaryMountainClusters(Map<HexCoord, TerrainType> terrain,
            Set<HexCoord> coordinates, HexCoord center, long seed) {
        int outerDistance = coordinates.stream().mapToInt(coord -> coord.distanceTo(center))
                .max().orElseThrow();
        HexCoord anchor = coordinates.stream()
                .filter(coord -> coord.distanceTo(center) >= Math.max(2, outerDistance - 2))
                .filter(coord -> terrain.get(coord) != TerrainType.SEA)
                .filter(coord -> terrain.get(coord) != TerrainType.MOUNTAIN_RANGE)
                .min(java.util.Comparator
                        .comparingLong((HexCoord coord) -> mix(seed + 101, coord.q(), coord.r()))
                        .thenComparingInt(HexCoord::q).thenComparingInt(HexCoord::r))
                .orElseThrow(() -> new IllegalStateException("No site for ordinary Mountains"));
        terrain.put(anchor, TerrainType.MOUNTAIN);
        anchor.neighbors().stream().filter(coordinates::contains)
                .filter(coord -> terrain.get(coord) != TerrainType.SEA)
                .filter(coord -> terrain.get(coord) != TerrainType.MOUNTAIN_RANGE)
                .limit(3)
                .forEach(coord -> terrain.put(coord, TerrainType.MOUNTAIN));
    }

    /** Produces one coherent, non-branching land-edge river away from the Hall start. */
    private static void createRiverEdges(GameMap map, HexCoord center, int radius, long seed) {
        List<civilization.model.map.HexEdge> best = List.of();
        int startDirection = Math.floorMod(seed, DIRECTIONS.size());
        for (int offset = 0; offset < DIRECTIONS.size(); offset++) {
            HexCoord direction = DIRECTIONS.get(
                    Math.floorMod(startDirection + offset, DIRECTIONS.size()));
            HexCoord current = add(center, scale(direction, 2));
            List<civilization.model.map.HexEdge> candidate = new ArrayList<>();
            for (int distance = 2; distance < radius - 1; distance++) {
                HexCoord next = add(current, direction);
                if (!map.contains(current) || !map.contains(next)) break;
                TerrainType first = map.requireTile(current).terrain();
                TerrainType second = map.requireTile(next).terrain();
                if (first == TerrainType.SEA || second == TerrainType.SEA
                        || first == TerrainType.MOUNTAIN_RANGE
                        || second == TerrainType.MOUNTAIN_RANGE) break;
                candidate.add(new civilization.model.map.HexEdge(current, next));
                current = next;
            }
            if (candidate.size() > best.size()) best = List.copyOf(candidate);
        }
        best.forEach(edge -> map.addRiver(edge.first(), edge.second()));
    }

    private static HexTile createTile(HexCoord coordinate, TerrainType terrain, long seed) {
        return switch (terrain) {
            case FOREST -> new HexTile(coordinate, terrain,
                    new ResourceDeposit(NaturalResourceType.WOOD,
                            65 + Math.floorMod(mix(seed, coordinate.q(), coordinate.r()), 36)));
            case MOUNTAIN -> {
                List<ResourceDeposit> deposits = new ArrayList<>();
                deposits.add(new ResourceDeposit(NaturalResourceType.STONE,
                        60 + Math.floorMod(mix(seed + 17, coordinate.q(), coordinate.r()), 41)));
                if (Math.floorMod(mix(seed + 31, coordinate.q(), coordinate.r()), 4) == 0) {
                    deposits.add(new ResourceDeposit(NaturalResourceType.IRON,
                            25 + Math.floorMod(mix(seed + 47, coordinate.q(), coordinate.r()), 26)));
                }
                yield new HexTile(coordinate, terrain, deposits);
            }
            case PLAINS -> {
                if (Math.floorMod(mix(seed + 59, coordinate.q(), coordinate.r()), 5) == 0) {
                    NaturalResourceType animal = Math.floorMod(coordinate.q() + coordinate.r(), 2) == 0
                            ? NaturalResourceType.CATTLE : NaturalResourceType.SHEEP;
                    yield new HexTile(coordinate, terrain, new ResourceDeposit(animal, 55));
                }
                yield new HexTile(coordinate, terrain);
            }
            case GRASSLAND -> {
                if (Math.floorMod(mix(seed + 97, coordinate.q(), coordinate.r()), 12) == 0) {
                    yield new HexTile(coordinate, terrain,
                            new ResourceDeposit(NaturalResourceType.HORSE, 1));
                }
                if (Math.floorMod(mix(seed + 71, coordinate.q(), coordinate.r()), 6) == 0) {
                    NaturalResourceType crop = Math.floorMod(coordinate.q() - coordinate.r(), 2) == 0
                            ? NaturalResourceType.WHEAT : NaturalResourceType.RICE;
                    yield new HexTile(coordinate, terrain, new ResourceDeposit(crop, 65));
                }
                yield new HexTile(coordinate, terrain);
            }
            case SEA -> new HexTile(coordinate, terrain,
                    new ResourceDeposit(NaturalResourceType.FISH,
                            60 + Math.floorMod(mix(seed + 83, coordinate.q(), coordinate.r()), 41)));
            case MOUNTAIN_RANGE -> new HexTile(coordinate, terrain);
        };
    }

    private static void validateGuarantees(GameMap map, HexCoord center, int radius) {
        if (!map.contains(center)) {
            throw new IllegalStateException("Generated map does not contain the Town Hall coordinate");
        }
        boolean nearbyForest = map.tiles().stream().anyMatch(tile ->
                tile.terrain() == TerrainType.FOREST && tile.coordinate().distanceTo(center) <= 2);
        if (!nearbyForest) {
            throw new IllegalStateException("Generated map lacks reachable Wood within two hexes");
        }
        boolean isolatedForest = map.tiles().stream()
                .filter(tile -> tile.terrain() == TerrainType.FOREST)
                .anyMatch(tile -> map.neighborsOf(tile.coordinate()).stream()
                        .noneMatch(neighbor -> neighbor.terrain() == TerrainType.FOREST));
        if (isolatedForest) {
            throw new IllegalStateException("Generated map contains an isolated forest hex");
        }
        long usableRoutes = map.neighborsOf(center).stream()
                .filter(tile -> tile.terrain() != TerrainType.SEA
                        && tile.terrain() != TerrainType.MOUNTAIN_RANGE)
                .filter(tile -> reachesInitialExpansion(map, tile.coordinate(), center,
                        Math.min(3, radius - 1)))
                .count();
        if (usableRoutes < 3) {
            throw new IllegalStateException("Generated map must provide at least three initial expansion routes");
        }
    }

    private static boolean reachesInitialExpansion(
            GameMap map, HexCoord start, HexCoord center, int targetDistance) {
        Set<HexCoord> visited = new HashSet<>();
        java.util.ArrayDeque<HexCoord> open = new java.util.ArrayDeque<>();
        visited.add(center);
        visited.add(start);
        open.add(start);
        while (!open.isEmpty()) {
            HexCoord current = open.removeFirst();
            if (current.distanceTo(center) >= targetDistance) return true;
            for (HexTile neighbor : map.neighborsOf(current)) {
                TerrainType terrain = neighbor.terrain();
                if (terrain != TerrainType.SEA && terrain != TerrainType.MOUNTAIN_RANGE
                        && visited.add(neighbor.coordinate())) {
                    open.addLast(neighbor.coordinate());
                }
            }
        }
        return false;
    }

    private static HexCoord add(HexCoord first, HexCoord second) {
        return new HexCoord(first.q() + second.q(), first.r() + second.r());
    }

    private static HexCoord scale(HexCoord coordinate, int amount) {
        return new HexCoord(coordinate.q() * amount, coordinate.r() * amount);
    }

    private static long mix(long seed, int q, int r) {
        long value = seed ^ (q * 0x9E3779B97F4A7C15L) ^ (r * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
