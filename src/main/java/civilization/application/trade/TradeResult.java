package civilization.application.trade;

import civilization.model.economy.ResourceType;
import civilization.model.trade.TradeEndpointKind;
import java.util.Objects;

public record TradeResult(
        TradeEndpointKind endpointKind,
        String endpointId,
        ResourceType source,
        ResourceType target,
        int sourceAmount,
        int targetAmount,
        int ratePercent,
        int turnNumber) {
    public TradeResult {
        Objects.requireNonNull(endpointKind, "endpointKind");
        endpointId = Objects.requireNonNull(endpointId, "endpointId").trim();
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (endpointId.isEmpty() || source == target || sourceAmount <= 0
                || targetAmount <= 0 || ratePercent <= 0 || turnNumber <= 0) {
            throw new IllegalArgumentException("Invalid trade result");
        }
    }
}
