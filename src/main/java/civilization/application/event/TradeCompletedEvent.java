package civilization.application.event;

import civilization.application.trade.TradeResult;
import java.util.Objects;

/** Integration hook for notifications, tribe relation changes, missions, and UI refresh. */
public record TradeCompletedEvent(TradeResult result) implements GameEvent {
    public TradeCompletedEvent { Objects.requireNonNull(result, "result"); }
}
