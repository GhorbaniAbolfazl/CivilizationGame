package civilization.model.trade;

import civilization.model.map.HexCoord;
import java.util.Objects;

/** Neutral, preplaced Trading Center; ownership is derived from its map hex. */
public final class TradingPost implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final TradingPostId id;
    private final HexCoord position;
    private final TradeCooldown tradeCooldown;

    public TradingPost(TradingPostId id, HexCoord position) {
        this(id, position, new TradeCooldown());
    }

    public TradingPost(TradingPostId id, HexCoord position, TradeCooldown tradeCooldown) {
        this.id = Objects.requireNonNull(id, "id");
        this.position = Objects.requireNonNull(position, "position");
        this.tradeCooldown = Objects.requireNonNull(tradeCooldown, "tradeCooldown");
    }

    public TradingPostId id() { return id; }
    public HexCoord position() { return position; }
    public TradeCooldown tradeCooldown() { return tradeCooldown; }
}
