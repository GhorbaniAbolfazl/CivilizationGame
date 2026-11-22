package civilization.model.map;

import java.util.Objects;

public final class ResourceDeposit implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final NaturalResourceType type;
    private final int originalAmount;
    private int remainingAmount;

    public ResourceDeposit(NaturalResourceType type, int amount) {
        this.type = Objects.requireNonNull(type, "type");
        if (amount <= 0) {
            throw new IllegalArgumentException("A resource deposit must start with a positive amount");
        }
        this.originalAmount = amount;
        this.remainingAmount = amount;
    }

    public NaturalResourceType type() {
        return type;
    }

    public int originalAmount() {
        return originalAmount;
    }

    public int remainingAmount() {
        return remainingAmount;
    }

    public boolean isDepleted() {
        return remainingAmount == 0;
    }

    public int extract(int requestedAmount) {
        if (requestedAmount < 0) {
            throw new IllegalArgumentException("Requested extraction cannot be negative");
        }
        int extracted = Math.min(requestedAmount, remainingAmount);
        remainingAmount -= extracted;
        return extracted;
    }
}
