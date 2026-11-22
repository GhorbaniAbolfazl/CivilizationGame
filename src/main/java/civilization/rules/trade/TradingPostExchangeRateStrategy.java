package civilization.rules.trade;

import civilization.model.trade.TradeQuote;
import civilization.model.trade.TradeRequest;
import java.util.Objects;

public final class TradingPostExchangeRateStrategy implements ExchangeRateStrategy {
    public static final int RATE_PERCENT = 80;

    @Override public TradeQuote quote(TradeRequest request) {
        Objects.requireNonNull(request, "request");
        return new TradeQuote(request.sourceAmount(),
                TradeMath.floorPercentage(request.sourceAmount(), RATE_PERCENT), RATE_PERCENT);
    }
}
