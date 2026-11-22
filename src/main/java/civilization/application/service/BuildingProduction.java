package civilization.application.service;

import civilization.model.building.BuildingId;
import civilization.model.economy.ResourceType;
import civilization.model.map.NaturalResourceType;
import civilization.model.map.HexCoord;

import java.util.Objects;

/** One immutable, prevalidated extraction entry in a turn's production plan. */
public record BuildingProduction(
        BuildingId buildingId,
        HexCoord sourceCoordinate,
        NaturalResourceType depositType,
        ResourceType outputType,
        int amount) {
    public BuildingProduction {
        Objects.requireNonNull(buildingId, "buildingId");
        Objects.requireNonNull(sourceCoordinate, "sourceCoordinate");
        Objects.requireNonNull(depositType, "depositType");
        Objects.requireNonNull(outputType, "outputType");
        if (amount <= 0) {
            throw new IllegalArgumentException("Building production amount must be positive");
        }
    }
}
