package civilization.model.trade;

/** Saveable turn stamp enforcing one successful trade per endpoint and turn. */
public final class TradeCooldown implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private int lastUsedTurn;

    public TradeCooldown() { this(0); }

    public TradeCooldown(int lastUsedTurn) {
        if (lastUsedTurn < 0) throw new IllegalArgumentException("Last-used turn cannot be negative");
        this.lastUsedTurn = lastUsedTurn;
    }

    public int lastUsedTurn() { return lastUsedTurn; }

    public boolean isAvailable(int currentTurn) {
        requireTurn(currentTurn);
        return lastUsedTurn != currentTurn;
    }

    public void markUsed(int currentTurn) {
        requireTurn(currentTurn);
        if (!isAvailable(currentTurn)) {
            throw new IllegalStateException("Trade endpoint was already used this turn");
        }
        lastUsedTurn = currentTurn;
    }

    private static void requireTurn(int turn) {
        if (turn <= 0) throw new IllegalArgumentException("Turn number must be positive");
    }
}
