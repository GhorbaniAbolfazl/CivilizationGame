package civilization.application.trade;

import civilization.model.trade.TradeQuote;
import java.util.Objects;
import java.util.Optional;

/** Read-only result used by controllers to display the exact committed trade outcome. */
public record TradePreview(boolean allowed, String reason, Optional<TradeQuote> quote) {
    public TradePreview {
        reason = Objects.requireNonNull(reason, "reason");
        quote = Objects.requireNonNull(quote, "quote");
        if (allowed && (!reason.isEmpty() || quote.isEmpty())) {
            throw new IllegalArgumentException("Allowed trade preview requires a quote and no reason");
        }
        if (!allowed && (reason.isBlank() || quote.isPresent())) {
            throw new IllegalArgumentException("Rejected trade preview requires only a reason");
        }
    }

    public static TradePreview allow(TradeQuote quote) {
        return new TradePreview(true, "", Optional.of(Objects.requireNonNull(quote, "quote")));
    }

    public static TradePreview reject(String reason) {
        return new TradePreview(false, reason, Optional.empty());
    }
}
