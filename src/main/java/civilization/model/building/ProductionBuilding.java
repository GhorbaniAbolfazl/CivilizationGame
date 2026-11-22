package civilization.model.building;

import civilization.model.map.HexCoord;
import civilization.model.identity.FactionId;

public final class ProductionBuilding extends Building implements WorkerStationBuilding {
    private static final long serialVersionUID = 1L;
    private final int workerCapacity;

    public ProductionBuilding(
            BuildingId id,
            BuildingType type,
            HexCoord position,
            int workerCapacity) {
        super(id, requireProductionType(type), position);
        if (workerCapacity <= 0) {
            throw new IllegalArgumentException("Worker capacity must be positive");
        }
        this.workerCapacity = workerCapacity;
    }

    public ProductionBuilding(
            BuildingId id, BuildingType type, HexCoord position, int workerCapacity,
            FactionId ownerId, int maximumHealth) {
        super(id, requireProductionType(type), position, ownerId, maximumHealth);
        if (workerCapacity <= 0) {
            throw new IllegalArgumentException("Worker capacity must be positive");
        }
        this.workerCapacity = workerCapacity;
    }

    public ProductionBuilding(
            BuildingId id,
            BuildingType type,
            HexCoord position,
            int workerCapacity,
            FactionId ownerId) {
        super(id, requireProductionType(type), position, ownerId);
        if (workerCapacity <= 0) {
            throw new IllegalArgumentException("Worker capacity must be positive");
        }
        this.workerCapacity = workerCapacity;
    }

    public int workerCapacity() {
        return workerCapacity;
    }

    private static BuildingType requireProductionType(BuildingType type) {
        if (type == null || !type.isProductionBuilding()) {
            throw new IllegalArgumentException("ProductionBuilding requires a producing building type");
        }
        return type;
    }
}
