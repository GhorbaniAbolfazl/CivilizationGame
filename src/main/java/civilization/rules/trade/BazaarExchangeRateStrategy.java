package civilization.rules.trade;

import civilization.model.trade.BazaarTradeLevel;
import civilization.model.trade.TradeQuote;
import civilization.model.trade.TradeRequest;
import java.util.Objects;

public final class BazaarExchangeRateStrategy implements ExchangeRateStrategy {
    private final BazaarTradeLevel level;

    public BazaarExchangeRateStrategy(BazaarTradeLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    @Override public TradeQuote quote(TradeRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.sourceAmount() != level.sourceAmount()) {
            throw new IllegalArgumentException(
                    level + " requires exactly " + level.sourceAmount() + " source resources");
        }
        return new TradeQuote(request.sourceAmount(),
                TradeMath.floorPercentage(request.sourceAmount(), level.ratePercent()),
                level.ratePercent());
    }
}
