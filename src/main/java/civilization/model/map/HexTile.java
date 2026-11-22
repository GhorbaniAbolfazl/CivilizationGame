package civilization.model.map;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class HexTile implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final HexCoord coordinate;
    private final TerrainType terrain;
    private final Map<NaturalResourceType, ResourceDeposit> resourceDeposits;

    public HexTile(HexCoord coordinate, TerrainType terrain) {
        this(coordinate, terrain, java.util.List.of());
    }

    public HexTile(HexCoord coordinate, TerrainType terrain, ResourceDeposit resourceDeposit) {
        this(coordinate, terrain, java.util.List.of(resourceDeposit));
    }

    public HexTile(HexCoord coordinate, TerrainType terrain, Collection<ResourceDeposit> resourceDeposits) {
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(resourceDeposits, "resourceDeposits");
        Map<NaturalResourceType, ResourceDeposit> indexed = new EnumMap<>(NaturalResourceType.class);
        for (ResourceDeposit deposit : resourceDeposits) {
            Objects.requireNonNull(deposit, "deposit");
            if (deposit.type().requiredTerrain() != terrain) {
                throw new IllegalArgumentException(deposit.type() + " is not valid on " + terrain);
            }
            if (indexed.put(deposit.type(), deposit) != null) {
                throw new IllegalArgumentException("Duplicate resource deposit: " + deposit.type());
            }
        }
        if (terrain == TerrainType.FOREST
                && !indexed.containsKey(NaturalResourceType.WOOD)) {
            throw new IllegalArgumentException("Every forest hex must contain wood");
        }
        if (terrain == TerrainType.MOUNTAIN
                && !indexed.containsKey(NaturalResourceType.STONE)) {
            throw new IllegalArgumentException("Every mountain hex must contain stone");
        }
        this.resourceDeposits = Map.copyOf(indexed);
    }

    public HexCoord coordinate() {
        return coordinate;
    }

    public TerrainType terrain() {
        return terrain;
    }

    public Map<NaturalResourceType, ResourceDeposit> resourceDeposits() {
        return resourceDeposits;
    }

    public Optional<ResourceDeposit> resourceDeposit(NaturalResourceType type) {
        return Optional.ofNullable(resourceDeposits.get(Objects.requireNonNull(type, "type")));
    }

    public boolean hasNaturalResource(NaturalResourceType type) {
        return resourceDeposits.containsKey(Objects.requireNonNull(type, "type"));
    }

    public boolean hasAvailableNaturalResource(NaturalResourceType type) {
        ResourceDeposit deposit = resourceDeposits.get(Objects.requireNonNull(type, "type"));
        return deposit != null && !deposit.isDepleted();
    }

    public boolean isResourceFree() {
        return resourceDeposits.isEmpty();
    }
}
