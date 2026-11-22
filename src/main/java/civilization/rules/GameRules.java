package civilization.rules;

import civilization.model.building.BuildingType;
import civilization.model.economy.ResourceBundle;
import civilization.model.map.TerrainType;
import civilization.model.technology.UpgradeId;
import civilization.model.technology.TechnologyCatalog;
import civilization.model.technology.TechnologyDefinition;
import civilization.model.technology.TechnologyId;
import civilization.model.building.HallLevel;
import civilization.model.unit.UnitType;
import civilization.model.infrastructure.InfrastructureType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class GameRules {
    private final Map<UnitType, UnitDefinition> units;
    private final Map<BuildingType, BuildingDefinition> buildings;
    private final Map<UpgradeId, UpgradeDefinition> upgrades;
    private final Map<HallLevel, HallUpgradeDefinition> hallUpgrades;
    private final TechnologyCatalog technologyCatalog;
    private final Map<TerrainType, Integer> movementCosts;
    private final Map<InfrastructureType, InfrastructureDefinition> infrastructure;
    private final ResourceBundle initialResources;
    private final ResourceBundle initialStorageCapacity;
    private final ResourceBundle townHallSafeguardProduction;
    private final ResourceBundle storageUpgradeIncrement;
    private final int initialUnitCap;
    private final int workerStationApCost;
    private final int borderExpansionApCost;
    private final int starvationApPenalty;
    private final int riverCrossingApCost;
    private final int roadMovementCost;

    public GameRules(
            Map<UnitType, UnitDefinition> units,
            Map<BuildingType, BuildingDefinition> buildings,
            Map<UpgradeId, UpgradeDefinition> upgrades,
            Map<HallLevel, HallUpgradeDefinition> hallUpgrades,
            java.util.Collection<TechnologyDefinition> technologies,
            Map<TerrainType, Integer> movementCosts,
            Map<InfrastructureType, InfrastructureDefinition> infrastructure,
            ResourceBundle initialResources,
            ResourceBundle initialStorageCapacity,
            ResourceBundle townHallSafeguardProduction,
            ResourceBundle storageUpgradeIncrement,
            int initialUnitCap,
            int workerStationApCost,
            int borderExpansionApCost,
            int starvationApPenalty,
            int riverCrossingApCost,
            int roadMovementCost) {
        this.units = immutableCompleteEnumMap(UnitType.class, units, "unit definitions");
        this.buildings = immutableCompleteEnumMap(BuildingType.class, buildings, "building definitions");
        this.upgrades = immutableCompleteEnumMap(UpgradeId.class, upgrades, "upgrade definitions");
        this.hallUpgrades = Map.copyOf(Objects.requireNonNull(hallUpgrades, "hallUpgrades"));
        if (!this.hallUpgrades.keySet().equals(
                java.util.Set.of(HallLevel.SETTLEMENT, HallLevel.CAPITAL))) {
            throw new IllegalArgumentException("Hall upgrade definitions must contain levels 2 and 3");
        }
        this.technologyCatalog = new TechnologyCatalog(technologies);
        this.movementCosts = immutableCompleteEnumMap(TerrainType.class, movementCosts, "movement costs");
        this.infrastructure = immutableCompleteEnumMap(
                InfrastructureType.class, infrastructure, "infrastructure definitions");
        this.initialResources = Objects.requireNonNull(initialResources, "initialResources");
        this.initialStorageCapacity = Objects.requireNonNull(initialStorageCapacity, "initialStorageCapacity");
        this.townHallSafeguardProduction = Objects.requireNonNull(
                townHallSafeguardProduction, "townHallSafeguardProduction");
        this.storageUpgradeIncrement = Objects.requireNonNull(storageUpgradeIncrement, "storageUpgradeIncrement");
        if (initialUnitCap < 5 || workerStationApCost <= 0
                || borderExpansionApCost <= 0 || starvationApPenalty <= 0
                || riverCrossingApCost <= 0 || roadMovementCost <= 0) {
            throw new IllegalArgumentException("Invalid global game rule values");
        }
        this.initialUnitCap = initialUnitCap;
        this.workerStationApCost = workerStationApCost;
        this.borderExpansionApCost = borderExpansionApCost;
        this.starvationApPenalty = starvationApPenalty;
        this.riverCrossingApCost = riverCrossingApCost;
        this.roadMovementCost = roadMovementCost;
        validateCrossRules();
    }

    public UnitDefinition unit(UnitType type) {
        return units.get(Objects.requireNonNull(type, "type"));
    }

    public BuildingDefinition building(BuildingType type) {
        return buildings.get(Objects.requireNonNull(type, "type"));
    }

    public UpgradeDefinition upgrade(UpgradeId id) {
        return upgrades.get(Objects.requireNonNull(id, "id"));
    }

    public HallUpgradeDefinition hallUpgrade(HallLevel target) {
        HallUpgradeDefinition definition = hallUpgrades.get(Objects.requireNonNull(target, "target"));
        if (definition == null) throw new IllegalArgumentException("No upgrade produces Hall level " + target);
        return definition;
    }

    public TechnologyDefinition technology(TechnologyId id) {
        return technologyCatalog.require(id);
    }

    public TechnologyCatalog technologyCatalog() { return technologyCatalog; }

    public int movementCost(TerrainType terrain) {
        return movementCosts.get(Objects.requireNonNull(terrain, "terrain"));
    }

    public InfrastructureDefinition infrastructure(InfrastructureType type) {
        return infrastructure.get(Objects.requireNonNull(type, "type"));
    }

    public ResourceBundle initialResources() {
        return initialResources;
    }

    public ResourceBundle initialStorageCapacity() {
        return initialStorageCapacity;
    }

    public ResourceBundle townHallSafeguardProduction() {
        return townHallSafeguardProduction;
    }

    public ResourceBundle storageUpgradeIncrement() {
        return storageUpgradeIncrement;
    }

    public int initialUnitCap() {
        return initialUnitCap;
    }

    public int workerStationApCost() {
        return workerStationApCost;
    }

    public int borderExpansionApCost() {
        return borderExpansionApCost;
    }

    public int starvationApPenalty() {
        return starvationApPenalty;
    }

    public int riverCrossingApCost() { return riverCrossingApCost; }

    public int roadMovementCost() { return roadMovementCost; }

    private void validateCrossRules() {
        int explorerAp = unit(UnitType.EXPLORER).actionPointCapacity();
        int explorerVision = unit(UnitType.EXPLORER).visionRadius();
        for (UnitType type : UnitType.values()) {
            if (type != UnitType.EXPLORER
                    && (explorerAp <= unit(type).actionPointCapacity()
                    || explorerVision <= unit(type).visionRadius())) {
                throw new IllegalArgumentException("Explorer must have the greatest AP and vision");
            }
        }
        int settlementAp = building(BuildingType.SETTLEMENT).constructionApCost();
        int maxOtherConstructionAp = buildings.values().stream()
                .filter(definition -> definition.type() != BuildingType.SETTLEMENT)
                .mapToInt(BuildingDefinition::constructionApCost)
                .max().orElse(0);
        if (settlementAp <= maxOtherConstructionAp) {
            throw new IllegalArgumentException("Settlement construction AP must be highest");
        }
        for (int movementCost : movementCosts.values()) {
            if (movementCost <= 0) {
                throw new IllegalArgumentException("Movement costs must be positive");
            }
        }
    }

    private static <E extends Enum<E>, V> Map<E, V> immutableCompleteEnumMap(
            Class<E> enumType, Map<E, V> source, String label) {
        Objects.requireNonNull(source, label);
        EnumMap<E, V> copy = new EnumMap<>(enumType);
        copy.putAll(source);
        if (copy.size() != enumType.getEnumConstants().length) {
            throw new IllegalArgumentException("Incomplete " + label);
        }
        if (copy.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " cannot contain null values");
        }
        return Map.copyOf(copy);
    }
}
