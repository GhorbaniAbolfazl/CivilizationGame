package civilization.model.trade;

/** The three fixed Bazaar transaction choices from the Phase 2 specification. */
public enum BazaarTradeLevel {
    LEVEL_1(10, 50),
    LEVEL_2(100, 60),
    LEVEL_3(500, 70);

    private final int sourceAmount;
    private final int ratePercent;

    BazaarTradeLevel(int sourceAmount, int ratePercent) {
        this.sourceAmount = sourceAmount;
        this.ratePercent = ratePercent;
    }

    public int sourceAmount() { return sourceAmount; }
    public int ratePercent() { return ratePercent; }
}
