package civilization.model.common;

/** Mutable bounded health value shared by later combat-capable entities. */
public final class Health implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private int maximum;
    private int current;

    public Health(int maximum) {
        this(maximum, maximum);
    }

    public Health(int maximum, int current) {
        if (maximum <= 0 || current < 0 || current > maximum) {
            throw new IllegalArgumentException("Health must satisfy 0 <= current <= maximum");
        }
        this.maximum = maximum;
        this.current = current;
    }

    public int current() { return current; }
    public int maximum() { return maximum; }
    public boolean isDepleted() { return current == 0; }

    public int damage(int amount) {
        requireNonNegative(amount);
        int applied = Math.min(current, amount);
        current -= applied;
        return applied;
    }

    public int heal(int amount) {
        requireNonNegative(amount);
        int applied = Math.min(maximum - current, amount);
        current += applied;
        return applied;
    }

    /** Raises max HP without implicitly healing; decreases are intentionally unsupported. */
    public void raiseMaximumTo(int newMaximum) {
        if (newMaximum < maximum) {
            throw new IllegalArgumentException("Maximum health cannot be reduced");
        }
        maximum = newMaximum;
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Health change cannot be negative");
    }
}
