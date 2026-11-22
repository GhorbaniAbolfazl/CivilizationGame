package civilization.rules.trade;

import civilization.model.trade.TradeQuote;
import civilization.model.trade.TradeRequest;

/** Strategy for endpoint-specific quantity, resource, and exchange-rate rules. */
public interface ExchangeRateStrategy {
    TradeQuote quote(TradeRequest request);
}
