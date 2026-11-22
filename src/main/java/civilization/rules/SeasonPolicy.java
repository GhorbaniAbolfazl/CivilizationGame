package civilization.rules;

import civilization.model.building.BuildingType;
import civilization.model.map.TerrainType;
import civilization.model.time.Season;

/** Pure global season modifiers shared by previews and committed simulation. */
public final class SeasonPolicy {
    public int productionAdjustment(Season season, BuildingType buildingType) {
        return switch (season) {
            case SPRING -> buildingType == BuildingType.FARM || buildingType == BuildingType.STABLE ? 1 : 0;
            case WINTER -> buildingType == BuildingType.FARM ? -1 : 0;
            case SUMMER, AUTUMN -> 0;
        };
    }

    public int movementAdjustment(Season season, TerrainType destination) {
        if (season == Season.AUTUMN && destination == TerrainType.SEA) return 1;
        if (season == Season.WINTER && destination != TerrainType.SEA) return 1;
        return 0;
    }
}
