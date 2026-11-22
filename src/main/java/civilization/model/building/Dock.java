package civilization.model.building;

import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;

/** Coastal player building used by the Coastal-tribe mission. */
public final class Dock extends Building implements WorkerStationBuilding {
    private static final long serialVersionUID = 1L;
    private final int workerCapacity;

    public Dock(BuildingId id, HexCoord position, FactionId ownerId, int maximumHealth) {
        this(id, position, ownerId, maximumHealth, 2);
    }

    public Dock(BuildingId id, HexCoord position, FactionId ownerId, int maximumHealth,
            int workerCapacity) {
        super(id, BuildingType.DOCK, position, ownerId, maximumHealth);
        if (workerCapacity <= 0) throw new IllegalArgumentException("Dock Worker capacity must be positive");
        this.workerCapacity = workerCapacity;
    }

    @Override
    public int workerCapacity() { return workerCapacity; }
}
