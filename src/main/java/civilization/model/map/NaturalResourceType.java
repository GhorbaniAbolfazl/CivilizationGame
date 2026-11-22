package civilization.model.map;

import civilization.model.economy.ResourceType;

public enum NaturalResourceType {
    WOOD(ResourceType.WOOD, TerrainType.FOREST),
    STONE(ResourceType.STONE, TerrainType.MOUNTAIN),
    IRON(ResourceType.IRON, TerrainType.MOUNTAIN),
    WHEAT(ResourceType.FOOD, TerrainType.GRASSLAND),
    RICE(ResourceType.FOOD, TerrainType.GRASSLAND),
    CATTLE(ResourceType.FOOD, TerrainType.PLAINS),
    SHEEP(ResourceType.FOOD, TerrainType.PLAINS),
    FISH(ResourceType.FOOD, TerrainType.SEA),
    /** Strategic placement marker; Horse is not added to the four-resource stockpile. */
    HORSE(null, TerrainType.GRASSLAND);

    private final ResourceType storedAs;
    private final TerrainType requiredTerrain;

    NaturalResourceType(ResourceType storedAs, TerrainType requiredTerrain) {
        this.storedAs = storedAs;
        this.requiredTerrain = requiredTerrain;
    }

    public ResourceType storedAs() {
        if (storedAs == null) {
            throw new IllegalStateException(this + " is a strategic marker, not a stored resource");
        }
        return storedAs;
    }

    public boolean isStoredResource() { return storedAs != null; }

    public TerrainType requiredTerrain() {
        return requiredTerrain;
    }
}
