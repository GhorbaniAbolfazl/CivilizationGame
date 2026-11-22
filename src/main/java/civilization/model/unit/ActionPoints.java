package civilization.model.unit;

public final class ActionPoints implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final int capacity;
    private int current;
    // PHASE 3 - Added in Phase 3
    private int temporaryBonusRemaining;

    public ActionPoints(int capacity) {
        this(capacity, capacity);
    }

    public ActionPoints(int capacity, int current) {
        if (capacity < 0) {
            throw new IllegalArgumentException("AP capacity cannot be negative");
        }
        if (current < 0 || current > capacity) {
            throw new IllegalArgumentException("Current AP must be between zero and capacity");
        }
        this.capacity = capacity;
        this.current = current;
    }

    public int capacity() {
        return capacity;
    }

    public int current() {
        return current;
    }

    public boolean canSpend(int amount) {
        requireNonNegative(amount);
        return current >= amount;
    }

    public void spend(int amount) {
        if (!canSpend(amount)) {
            throw new IllegalStateException("Insufficient AP");
        }
        current -= amount;
        // PHASE 3 - Added in Phase 3
        temporaryBonusRemaining = Math.max(0, temporaryBonusRemaining - amount);
    }

    public void refill() {
        // PHASE 3 - Added in Phase 3
        temporaryBonusRemaining = 0;
        current = capacity;
    }

    public void refillTo(int effectiveCapacity) {
        if (effectiveCapacity < 0 || effectiveCapacity > capacity) {
            throw new IllegalArgumentException("Effective AP capacity is outside base capacity");
        }
        // PHASE 3 - Added in Phase 3
        temporaryBonusRemaining = 0;
        current = effectiveCapacity;
    }

    public void restore(int amount) {
        requireNonNegative(amount);
        current = Math.min(capacity, Math.addExact(current, amount));
    }

    // PHASE 3 - Added in Phase 3
    public void grantTemporaryBonus(int amount) {
        requireNonNegative(amount);
        if (amount == 0) throw new IllegalArgumentException("Temporary AP bonus must be positive");
        current = Math.addExact(current, amount);
        temporaryBonusRemaining = Math.addExact(temporaryBonusRemaining, amount);
    }

    // PHASE 3 - Added in Phase 3
    public int temporaryBonusRemaining() { return temporaryBonusRemaining; }

    // PHASE 3 - Added in Phase 3
    public void clearTemporaryBonus() {
        current -= Math.min(current, temporaryBonusRemaining);
        temporaryBonusRemaining = 0;
    }

    public int applyPenalty(int amount) {
        requireNonNegative(amount);
        int applied = Math.min(current, amount);
        current -= applied;
        return applied;
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("AP amount cannot be negative");
        }
    }
}
