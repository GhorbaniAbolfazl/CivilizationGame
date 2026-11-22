package civilization.model.trade;

import civilization.model.economy.ResourceType;
import java.util.Objects;

public record TradeRequest(ResourceType source, ResourceType target, int sourceAmount) implements java.io.Serializable {
    public TradeRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (source == target) throw new IllegalArgumentException("Trade resources must be distinct");
        if (sourceAmount <= 0) throw new IllegalArgumentException("Trade amount must be positive");
    }
}
