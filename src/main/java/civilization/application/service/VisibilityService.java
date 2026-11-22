package civilization.application.service;

import civilization.model.building.Building;
import civilization.model.game.GameState;
import civilization.model.map.HexCoord;
import civilization.model.unit.Unit;
import civilization.model.unit.ExplorerUnit;
import civilization.rules.GameRules;

import java.util.HashSet;
import java.util.Set;

public final class VisibilityService {
    public void refresh(GameState state, GameRules rules) {
        Set<HexCoord> visible = new HashSet<>();
        Set<HexCoord> explorerVisible = new HashSet<>();
        for (Unit unit : state.player().units()) {
            if (unit.isActive()) {
                var footprint = state.map().coordinatesWithin(
                        unit.position(), rules.unit(unit.type()).visionRadius());
                visible.addAll(footprint);
                if (unit instanceof ExplorerUnit) {
                    explorerVisible.addAll(footprint);
                }
            }
        }
        for (Building building : state.player().allBuildings()) {
            if (building.isActive()) {
                visible.addAll(state.map().coordinatesWithin(
                        building.position(), rules.building(building.type()).visionRadius()));
            }
        }
        state.player().visibility().replaceCurrentlyVisible(visible);
        state.player().visibility().discoverAllByExplorer(explorerVisible);
    }

    public Set<HexCoord> withinRadius(GameState state, HexCoord center, int radius) {
        return java.util.Set.copyOf(state.map().coordinatesWithin(center, radius));
    }
}
