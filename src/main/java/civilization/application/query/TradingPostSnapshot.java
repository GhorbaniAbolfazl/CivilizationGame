package civilization.application.query;

import civilization.model.map.HexCoord;
import civilization.model.trade.TradingPostId;
import java.util.Objects;

public record TradingPostSnapshot(
        TradingPostId id,
        HexCoord position,
        boolean controlled,
        boolean availableThisTurn) {
    public TradingPostSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
    }
}
