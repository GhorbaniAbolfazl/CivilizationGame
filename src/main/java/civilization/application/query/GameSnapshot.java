package civilization.application.query;

import civilization.model.technology.UpgradeId;

import java.util.List;
import java.util.Set;

public record GameSnapshot(
        long stateVersion,
        MapSnapshot map,
        List<UnitDetailSnapshot> units,
        List<BuildingSnapshot> buildings,
        HudSnapshot hud,
        Set<UpgradeId> completedUpgrades,
        TownHallSnapshot townHall) {

    public GameSnapshot {
        units = List.copyOf(units);
        buildings = List.copyOf(buildings);
        completedUpgrades = Set.copyOf(completedUpgrades);
        java.util.Objects.requireNonNull(townHall, "townHall");
    }
}
