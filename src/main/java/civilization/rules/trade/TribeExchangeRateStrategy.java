package civilization.rules.trade;

import civilization.model.economy.ResourceType;
import civilization.model.trade.TradeQuote;
import civilization.model.trade.TradeRequest;
import civilization.model.trade.TribeTradeEndpoint;
import java.util.Objects;

public final class TribeExchangeRateStrategy implements ExchangeRateStrategy {
    private final TribeTradeEndpoint endpoint;

    public TribeExchangeRateStrategy(TribeTradeEndpoint endpoint) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    }

    @Override public TradeQuote quote(TradeRequest request) {
        Objects.requireNonNull(request, "request");
        int baseRate = switch (endpoint.profile()) {
            case FARMER, MOUNTAIN, COASTAL -> 75;
            case TRADER -> 80;
        };
        boolean validTarget = switch (endpoint.profile()) {
            case FARMER, COASTAL -> request.target() == ResourceType.FOOD;
            case MOUNTAIN -> request.target() == ResourceType.STONE
                    || request.target() == ResourceType.IRON;
            case TRADER -> true;
        };
        if (!validTarget) {
            throw new IllegalArgumentException(
                    endpoint.profile() + " tribe cannot supply " + request.target());
        }
        int effectiveRate = Math.addExact(baseRate, endpoint.rateBonusPercent());
        return new TradeQuote(request.sourceAmount(),
                TradeMath.floorPercentage(request.sourceAmount(), effectiveRate), effectiveRate);
    }
}
