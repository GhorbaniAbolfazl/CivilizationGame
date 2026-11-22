package civilization.model.building;

import civilization.model.economy.ResourceType;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.TerrainType;

import java.util.Optional;
import java.util.Set;

public enum BuildingType {
    TOWN_HALL(null, Set.of(), null),
    LUMBER_MILL(TerrainType.FOREST, Set.of(NaturalResourceType.WOOD), ResourceType.WOOD),
    STONE_MINE(TerrainType.MOUNTAIN, Set.of(NaturalResourceType.STONE), ResourceType.STONE),
    IRON_MINE(TerrainType.MOUNTAIN, Set.of(NaturalResourceType.IRON), ResourceType.IRON),
    FARM(TerrainType.GRASSLAND,
            Set.of(NaturalResourceType.WHEAT, NaturalResourceType.RICE),
            ResourceType.FOOD),
    STABLE(TerrainType.PLAINS,
            Set.of(NaturalResourceType.CATTLE, NaturalResourceType.SHEEP),
            ResourceType.FOOD),
    SETTLEMENT(null, Set.of(), null),
    MILITARY_STABLE(null, Set.of(), null),
    BAZAAR(null, Set.of(), null),
    DOCK(null, Set.of(NaturalResourceType.FISH), ResourceType.FOOD),
    MONUMENT(TerrainType.PLAINS, Set.of(), null),
    // PHASE 3 - Added in Phase 3
    APOTHECARY(TerrainType.PLAINS, Set.of(), null);

    private final TerrainType requiredTerrain;
    private final Set<NaturalResourceType> eligibleNaturalResources;
    private final ResourceType producedResource;

    BuildingType(
            TerrainType requiredTerrain,
            Set<NaturalResourceType> eligibleNaturalResources,
            ResourceType producedResource) {
        this.requiredTerrain = requiredTerrain;
        this.eligibleNaturalResources = Set.copyOf(eligibleNaturalResources);
        this.producedResource = producedResource;
    }

    public Optional<TerrainType> requiredTerrain() {
        return Optional.ofNullable(requiredTerrain);
    }

    public Set<NaturalResourceType> eligibleNaturalResources() {
        return eligibleNaturalResources;
    }

    public Optional<ResourceType> producedResource() {
        return Optional.ofNullable(producedResource);
    }

    public boolean isProductionBuilding() {
        return producedResource != null;
    }
}
