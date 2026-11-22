package civilization.model.building;

import civilization.model.economy.ResourceStockpile;
import civilization.model.map.HexCoord;
import civilization.model.technology.ProductionQueue;
import civilization.model.identity.FactionId;

import java.util.Objects;

public final class TownHall extends Building {
    private static final long serialVersionUID = 1L;
    private final ResourceStockpile stockpile;
    private final ProductionQueue productionQueue;
    private HallLevel level;
    private int defense;

    public TownHall(
            BuildingId id,
            HexCoord position,
            ResourceStockpile stockpile,
            ProductionQueue productionQueue) {
        this(id, position, stockpile, productionQueue, FactionId.PLAYER);
    }

    public TownHall(
            BuildingId id,
            HexCoord position,
            ResourceStockpile stockpile,
            ProductionQueue productionQueue,
            FactionId ownerId) {
        super(id, BuildingType.TOWN_HALL, position, ownerId, 200);
        this.stockpile = Objects.requireNonNull(stockpile, "stockpile");
        this.productionQueue = Objects.requireNonNull(productionQueue, "productionQueue");
        this.level = HallLevel.BASE_CAMP;
        this.defense = 10;
    }

    public ResourceStockpile stockpile() {
        return stockpile;
    }

    public ProductionQueue productionQueue() {
        return productionQueue;
    }

    public HallLevel level() { return level; }
    public int defense() { return defense; }

    public void upgradeTo(HallLevel target) {
        Objects.requireNonNull(target, "target");
        if (level == HallLevel.CAPITAL || level.next() != target) {
            throw new IllegalStateException("Town Hall upgrades must advance exactly one level");
        }
        level = target;
    }

    public void applyDefensiveArchitecture() {
        health().raiseMaximumTo(350);
        defense = 30;
    }
}
