package civilization.model.building;

import civilization.model.common.Health;
import civilization.model.identity.FactionId;
import civilization.model.map.HexCoord;
import civilization.model.trade.TradeCooldown;
import java.util.Objects;

/** Player-owned trade building offering all three fixed Bazaar trade levels. */
public final class Bazaar extends Building {
    private static final long serialVersionUID = 1L;
    private final TradeCooldown tradeCooldown;

    public Bazaar(BuildingId id, HexCoord position, int maximumHealth) {
        this(id, position, FactionId.PLAYER, new Health(maximumHealth), new TradeCooldown());
    }

    public Bazaar(BuildingId id, HexCoord position, FactionId ownerId, int maximumHealth) {
        this(id, position, ownerId, new Health(maximumHealth), new TradeCooldown());
    }

    public Bazaar(BuildingId id, HexCoord position, FactionId ownerId,
            Health health, TradeCooldown tradeCooldown) {
        super(id, BuildingType.BAZAAR, position, ownerId, health);
        this.tradeCooldown = Objects.requireNonNull(tradeCooldown, "tradeCooldown");
    }

    public TradeCooldown tradeCooldown() { return tradeCooldown; }
}
