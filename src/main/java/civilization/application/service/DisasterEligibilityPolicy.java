package civilization.application.service;

import civilization.model.disaster.DisasterType;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.map.TerrainType;
import civilization.model.time.Season;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashSet;

/** Pure terrain/season eligibility rules for mandatory disasters. */
public final class DisasterEligibilityPolicy {
    public boolean eligible(GameState state, DisasterType type, HexCoord center) {
        return state.map().contains(center) && eligibleCenters(state, type).contains(center);
    }

    /** Computes shared reachability/water context once per disaster type selection. */
    public Set<HexCoord> eligibleCenters(GameState state, DisasterType type) {
        Set<HexCoord> reachableForest = type == DisasterType.BEAR_ATTACK
                ? reachableForests(state) : Set.of();
        Set<HexCoord> nearWater = type == DisasterType.FLOOD && state.season().season() == Season.AUTUMN
                ? nearWaterCenters(state) : Set.of();
        LinkedHashSet<HexCoord> result = new LinkedHashSet<>();
        for (var tile : state.map().tiles()) {
            HexCoord center = tile.coordinate();
            TerrainType terrain = tile.terrain();
            boolean eligible = switch (type) {
                case EARTHQUAKE -> isDryLand(terrain);
                case FLOOD -> state.season().season() == Season.AUTUMN
                        && (terrain == TerrainType.PLAINS || terrain == TerrainType.GRASSLAND
                        || terrain == TerrainType.FOREST) && nearWater.contains(center);
                case BEAR_ATTACK -> terrain == TerrainType.FOREST
                        && reachableForest.contains(center)
                        && !state.world().disasters().bearAttackOnCooldown(
                        center, state.turn().turnNumber());
            };
            if (eligible) result.add(center);
        }
        return Set.copyOf(result);
    }

    public static boolean isDryLand(TerrainType terrain) {
        return terrain != TerrainType.SEA;
    }

    private static Set<HexCoord> nearWaterCenters(GameState state) {
        Set<HexCoord> result = new HashSet<>();
        for (var tile : state.map().tiles()) {
            if (tile.terrain() != TerrainType.SEA) continue;
            tile.coordinate().neighbors().stream().filter(state.map()::contains).forEach(result::add);
        }
        for (var edge : state.map().riverEdges()) {
            result.addAll(state.map().coordinatesWithin(edge.first(), 1));
            result.addAll(state.map().coordinatesWithin(edge.second(), 1));
        }
        return result;
    }

    private static Set<HexCoord> reachableForests(GameState state) {
        HexCoord start = state.player().townHall().position();
        Set<HexCoord> visited = new HashSet<>();
        ArrayDeque<HexCoord> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            HexCoord current = queue.removeFirst();
            for (HexCoord neighbor : current.neighbors()) {
                if (!state.map().contains(neighbor) || visited.contains(neighbor)) continue;
                TerrainType terrain = state.map().requireTile(neighbor).terrain();
                if (terrain == TerrainType.SEA || terrain == TerrainType.MOUNTAIN_RANGE) continue;
                visited.add(neighbor);
                queue.addLast(neighbor);
            }
        }
        return visited.stream().filter(coord -> state.map().requireTile(coord).terrain()
                == TerrainType.FOREST).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
