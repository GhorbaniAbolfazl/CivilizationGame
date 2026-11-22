package civilization.model.trade;

public record TradeQuote(int sourceAmount, int targetAmount, int ratePercent) implements java.io.Serializable {
    public TradeQuote {
        if (sourceAmount <= 0 || targetAmount < 0 || ratePercent <= 0) {
            throw new IllegalArgumentException("Invalid trade quote");
        }
    }
}
