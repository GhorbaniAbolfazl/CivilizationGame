package civilization.application.service;

import civilization.model.building.Building;
import civilization.model.building.BuildingId;
import civilization.model.building.BuildingType;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.map.HexEdge;
import civilization.model.map.TerrainType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Pure, dynamic adjacency calculation. No derived bonus is stored in game state. */
public final class AdjacencyService {
    public Map<BuildingId, Integer> productionBonuses(GameState state) {
        Objects.requireNonNull(state, "state");
        Map<HexCoord, Building> active = new HashMap<>();
        state.player().constructedBuildings().stream().filter(Building::isActive)
                .forEach(building -> active.put(building.position(), building));
        Map<BuildingId, Integer> bonuses = new HashMap<>();
        Set<HexEdge> farmEdges = new HashSet<>();
        for (Building building : active.values()) {
            if (building.type() == BuildingType.FARM) {
                for (var neighbor : state.map().neighborsOf(building.position())) {
                    Building other = active.get(neighbor.coordinate());
                    if (other != null && other.type() == BuildingType.FARM) {
                        HexEdge edge = new HexEdge(building.position(), other.position());
                        if (farmEdges.add(edge)) {
                            Building recipient = active.get(edge.first());
                            bonuses.merge(recipient.id(), 1, Math::addExact);
                        }
                    }
                }
            } else if (building.type() == BuildingType.LUMBER_MILL) {
                boolean coastal = state.map().neighborsOf(building.position()).stream()
                        .anyMatch(tile -> tile.terrain() == TerrainType.SEA);
                if (coastal) bonuses.put(building.id(), 2);
            } else if (building.type() == BuildingType.STONE_MINE
                    || building.type() == BuildingType.IRON_MINE) {
                long ordinaryMountains = state.map().neighborsOf(building.position()).stream()
                        .filter(tile -> tile.terrain() == TerrainType.MOUNTAIN).count();
                if (ordinaryMountains >= 2) bonuses.put(building.id(), 1);
            }
        }
        return Map.copyOf(bonuses);
    }
}
