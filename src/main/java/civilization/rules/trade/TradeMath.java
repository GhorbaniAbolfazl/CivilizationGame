package civilization.rules.trade;

final class TradeMath {
    private TradeMath() { }

    static int floorPercentage(int amount, int percentage) {
        if (amount <= 0 || percentage <= 0) {
            throw new IllegalArgumentException("Trade amount and percentage must be positive");
        }
        return Math.toIntExact(((long) amount * percentage) / 100L);
    }
}
