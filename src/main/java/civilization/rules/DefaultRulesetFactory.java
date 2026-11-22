package civilization.rules;

import civilization.model.building.BuildingType;
import civilization.model.economy.ResourceBundle;
import civilization.model.economy.ResourceType;
import civilization.model.map.TerrainType;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.TechnologyDefinition;
import civilization.model.technology.TechnologyId;
import civilization.model.building.HallLevel;
import civilization.model.unit.UnitType;
import civilization.model.infrastructure.InfrastructureType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class DefaultRulesetFactory {
    private DefaultRulesetFactory() {
    }

    public static GameRules create() {
        return new GameRules(
                unitDefinitions(),
                buildingDefinitions(),
                upgradeDefinitions(),
                hallUpgradeDefinitions(),
                technologyDefinitions(),
                movementCosts(),
                infrastructureDefinitions(),
                ResourceBundle.of(16, 18, 8, 0),
                ResourceBundle.of(100, 100, 100, 100),
                ResourceBundle.of(1, 1, 0, 0),
                ResourceBundle.of(25, 25, 25, 25),
                7,
                1,
                2,
                1,
                1,
                1);
    }

    private static Map<UnitType, UnitDefinition> unitDefinitions() {
        EnumMap<UnitType, UnitDefinition> definitions = new EnumMap<>(UnitType.class);
        definitions.put(UnitType.EXPLORER,
                new UnitDefinition(UnitType.EXPLORER, 8, 3, 1,
                        ResourceBundle.of(5, 4, 0, 0), 2, 0));
        definitions.put(UnitType.BUILDER,
                new UnitDefinition(UnitType.BUILDER, 5, 1, 1,
                        ResourceBundle.of(4, 8, 2, 0), 2, 3));
        definitions.put(UnitType.WORKER,
                new UnitDefinition(UnitType.WORKER, 4, 1, 1,
                        ResourceBundle.of(4, 3, 0, 0), 1, 0));
        definitions.put(UnitType.BORDER_EXPANDER,
                new UnitDefinition(UnitType.BORDER_EXPANDER, 5, 1, 1,
                        ResourceBundle.of(6, 8, 5, 1), 3, 0));
        definitions.put(UnitType.SWORDSMAN,
                new UnitDefinition(UnitType.SWORDSMAN, 2, 1, 1,
                        ResourceBundle.of(20, 10, 0, 0), 2, 0, 1, 1));
        definitions.put(UnitType.ARCHER,
                new UnitDefinition(UnitType.ARCHER, 2, 2, 1,
                        ResourceBundle.of(25, 15, 0, 5), 3, 0, 1, 2));
        definitions.put(UnitType.CAVALRY,
                new UnitDefinition(UnitType.CAVALRY, 4, 2, 1,
                        ResourceBundle.of(35, 20, 0, 10), 4, 0, 2, 1));
        return definitions;
    }

    private static Map<HallLevel, HallUpgradeDefinition> hallUpgradeDefinitions() {
        return Map.of(
                HallLevel.SETTLEMENT,
                new HallUpgradeDefinition(HallLevel.SETTLEMENT,
                        ResourceBundle.of(0, 50, 50, 0), 3,
                        ResourceBundle.of(50, 50, 50, 50), 50),
                HallLevel.CAPITAL,
                new HallUpgradeDefinition(HallLevel.CAPITAL,
                        ResourceBundle.of(0, 0, 100, 50), 5,
                        ResourceBundle.of(350, 350, 350, 350), 0));
    }

    private static java.util.List<TechnologyDefinition> technologyDefinitions() {
        return java.util.List.of(
                new TechnologyDefinition(TechnologyId.NAVIGATION, Set.of(),
                        HallLevel.SETTLEMENT, ResourceBundle.of(0, 80, 0, 0), 4),
                new TechnologyDefinition(TechnologyId.STEEL_TOOLS, Set.of(),
                        HallLevel.SETTLEMENT, ResourceBundle.of(0, 0, 0, 40), 3),
                new TechnologyDefinition(TechnologyId.DEFENSIVE_ARCHITECTURE, Set.of(),
                        HallLevel.CAPITAL, ResourceBundle.of(0, 0, 100, 0), 4));
    }

    private static Map<BuildingType, BuildingDefinition> buildingDefinitions() {
        EnumMap<BuildingType, BuildingDefinition> definitions = new EnumMap<>(BuildingType.class);
        definitions.put(BuildingType.TOWN_HALL,
                new BuildingDefinition(BuildingType.TOWN_HALL, ResourceBundle.ZERO,
                        0, 0, 0, null, 0, 2, 0, 200));
        definitions.put(BuildingType.LUMBER_MILL,
                new BuildingDefinition(BuildingType.LUMBER_MILL, ResourceBundle.of(0, 5, 0, 0),
                        1, 2, 4, ResourceType.WOOD, 1, 1, 0));
        definitions.put(BuildingType.STONE_MINE,
                new BuildingDefinition(BuildingType.STONE_MINE, ResourceBundle.of(0, 8, 0, 0),
                        2, 2, 4, ResourceType.WOOD, 1, 1, 0));
        definitions.put(BuildingType.IRON_MINE,
                new BuildingDefinition(BuildingType.IRON_MINE, ResourceBundle.of(0, 10, 3, 0),
                        2, 2, 2, ResourceType.WOOD, 1, 1, 0));
        definitions.put(BuildingType.FARM,
                new BuildingDefinition(BuildingType.FARM, ResourceBundle.of(0, 4, 0, 0),
                        1, 2, 4, ResourceType.WOOD, 1, 1, 0));
        definitions.put(BuildingType.STABLE,
                new BuildingDefinition(BuildingType.STABLE, ResourceBundle.of(0, 6, 0, 0),
                        1, 2, 3, ResourceType.WOOD, 1, 1, 0));
        definitions.put(BuildingType.SETTLEMENT,
                new BuildingDefinition(BuildingType.SETTLEMENT, ResourceBundle.of(0, 10, 12, 4),
                        3, 0, 0, ResourceType.STONE, 1, 1, 3));
        definitions.put(BuildingType.MILITARY_STABLE,
                new BuildingDefinition(BuildingType.MILITARY_STABLE,
                        ResourceBundle.of(0, 20, 10, 5),
                        2, 0, 0, ResourceType.WOOD, 1, 1, 0, 100));
        definitions.put(BuildingType.BAZAAR,
                new BuildingDefinition(BuildingType.BAZAAR,
                        ResourceBundle.of(0, 30, 20, 5),
                        2, 0, 0, ResourceType.WOOD, 1, 1, 0, 120));
        definitions.put(BuildingType.DOCK,
                new BuildingDefinition(BuildingType.DOCK,
                        ResourceBundle.of(0, 25, 10, 5),
                        2, 2, 3, ResourceType.WOOD, 1, 1, 0, 120));
        definitions.put(BuildingType.MONUMENT,
                new BuildingDefinition(BuildingType.MONUMENT,
                        ResourceBundle.of(0, 10, 20, 0),
                        2, 0, 0, null, 0, 1, 0, 100));
        // PHASE 3 - Added in Phase 3
        definitions.put(BuildingType.APOTHECARY,
                new BuildingDefinition(BuildingType.APOTHECARY,
                        ResourceBundle.of(0, 25, 20, 10),
                        2, 0, 0, null, 0, 1, 0, 100));
        return definitions;
    }

    private static Map<InfrastructureType, InfrastructureDefinition> infrastructureDefinitions() {
        return Map.of(
                InfrastructureType.ROAD,
                new InfrastructureDefinition(InfrastructureType.ROAD,
                        ResourceBundle.of(0, 2, 0, 0), 1, true, 0),
                InfrastructureType.WALL,
                new InfrastructureDefinition(InfrastructureType.WALL,
                        ResourceBundle.of(0, 5, 10, 0), 2, true, 100));
    }

    private static Map<UpgradeId, UpgradeDefinition> upgradeDefinitions() {
        EnumMap<UpgradeId, UpgradeDefinition> definitions = new EnumMap<>(UpgradeId.class);
        definitions.put(UpgradeId.STORAGE_1,
                new UpgradeDefinition(UpgradeId.STORAGE_1, ResourceBundle.of(0, 10, 5, 0), 2, Set.of()));
        definitions.put(UpgradeId.STORAGE_2,
                new UpgradeDefinition(UpgradeId.STORAGE_2, ResourceBundle.of(0, 15, 10, 3), 3,
                        Set.of(UpgradeId.STORAGE_1)));
        definitions.put(UpgradeId.STONE_MINING,
                new UpgradeDefinition(UpgradeId.STONE_MINING, ResourceBundle.of(0, 8, 0, 0), 2, Set.of()));
        definitions.put(UpgradeId.IRON_MINING,
                new UpgradeDefinition(UpgradeId.IRON_MINING, ResourceBundle.of(0, 10, 8, 0), 3,
                        Set.of(UpgradeId.STONE_MINING)));
        definitions.put(UpgradeId.PROFESSIONAL_TOOLS,
                new UpgradeDefinition(UpgradeId.PROFESSIONAL_TOOLS, ResourceBundle.of(0, 12, 10, 5), 3,
                        Set.of(UpgradeId.STONE_MINING, UpgradeId.IRON_MINING)));
        definitions.put(UpgradeId.SETTLEMENT_CONSTRUCTION,
                new UpgradeDefinition(UpgradeId.SETTLEMENT_CONSTRUCTION, ResourceBundle.of(0, 10, 8, 2), 3,
                        Set.of()));
        return definitions;
    }

    private static Map<TerrainType, Integer> movementCosts() {
        return Map.of(
                TerrainType.PLAINS, 1,
                TerrainType.GRASSLAND, 1,
                TerrainType.FOREST, 2,
                TerrainType.MOUNTAIN, 4,
                TerrainType.SEA, 1,
                TerrainType.MOUNTAIN_RANGE, 4);
    }
}
