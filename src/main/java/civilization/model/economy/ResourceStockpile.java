package civilization.model.economy;

import java.util.EnumMap;
import java.util.Objects;
import java.io.IOException;
import java.io.ObjectInputStream;

public final class ResourceStockpile implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final EnumMap<ResourceType, Integer> amounts = new EnumMap<>(ResourceType.class);
    private final EnumMap<ResourceType, Integer> capacities = new EnumMap<>(ResourceType.class);
    // PHASE 3 - Added in Phase 3
    private EnumMap<ResourceType, Integer> reserved = emptyResourceMap();

    public ResourceStockpile(ResourceBundle initialAmounts, ResourceBundle capacities) {
        Objects.requireNonNull(initialAmounts, "initialAmounts");
        Objects.requireNonNull(capacities, "capacities");
        for (ResourceType type : ResourceType.values()) {
            int capacity = capacities.amountOf(type);
            int amount = initialAmounts.amountOf(type);
            if (amount > capacity) {
                throw new IllegalArgumentException(type + " amount exceeds capacity");
            }
            this.amounts.put(type, amount);
            this.capacities.put(type, capacity);
        }
    }

    public int amountOf(ResourceType type) {
        return amounts.get(Objects.requireNonNull(type, "type"));
    }

    // PHASE 3 - Added in Phase 3
    public int reservedAmountOf(ResourceType type) {
        return reserved.get(Objects.requireNonNull(type, "type"));
    }

    // PHASE 3 - Added in Phase 3
    public int availableAmountOf(ResourceType type) {
        return amountOf(type) - reservedAmountOf(type);
    }

    public int capacityOf(ResourceType type) {
        return capacities.get(Objects.requireNonNull(type, "type"));
    }

    public ResourceBundle amounts() {
        return new ResourceBundle(amounts);
    }

    // PHASE 3 - Added in Phase 3
    public ResourceBundle reservedAmounts() {
        return new ResourceBundle(reserved);
    }

    // PHASE 3 - Added in Phase 3
    public ResourceBundle availableAmounts() {
        EnumMap<ResourceType, Integer> available = new EnumMap<>(ResourceType.class);
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) {
            available.put(type, availableAmountOf(type));
        }
        return new ResourceBundle(available);
    }

    public ResourceBundle capacities() {
        return new ResourceBundle(capacities);
    }

    public ResourceBundle remainingCapacity() {
        EnumMap<ResourceType, Integer> remaining = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            remaining.put(type, capacityOf(type) - amountOf(type));
        }
        return new ResourceBundle(remaining);
    }

    public boolean canAfford(ResourceBundle cost) {
        Objects.requireNonNull(cost, "cost");
        // PHASE 3 - Added in Phase 3
        return cost.asMap().entrySet().stream()
                .allMatch(entry -> availableAmountOf(entry.getKey()) >= entry.getValue());
    }

    // PHASE 3 - Added in Phase 3
    public boolean canReserve(ResourceBundle resources) {
        return canAfford(Objects.requireNonNull(resources, "resources"));
    }

    // PHASE 3 - Added in Phase 3
    public void reserve(ResourceBundle resources) {
        Objects.requireNonNull(resources, "resources");
        if (resources.isZero()) throw new IllegalArgumentException("Reserved resources must be positive");
        if (!canReserve(resources)) throw new IllegalStateException("Insufficient available resources");
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) {
            reserved.put(type, Math.addExact(reservedAmountOf(type), resources.amountOf(type)));
        }
    }

    // PHASE 3 - Added in Phase 3
    public void releaseReservation(ResourceBundle resources) {
        requireReservation(resources);
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) {
            reserved.put(type, reservedAmountOf(type) - resources.amountOf(type));
        }
    }

    // PHASE 3 - Added in Phase 3
    public static void settleReservedExchange(ResourceStockpile offerer,
                                               ResourceBundle offered,
                                               ResourceStockpile recipient,
                                               ResourceBundle requested) {
        Objects.requireNonNull(offerer, "offerer");
        Objects.requireNonNull(offered, "offered");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(requested, "requested");
        if (offerer == recipient) throw new IllegalArgumentException("Trade players must be distinct");
        offerer.requireReservation(offered);
        if (!recipient.canAfford(requested)) {
            throw new IllegalStateException("Recipient has insufficient available resources");
        }
        EnumMap<ResourceType, Integer> offererFinal = new EnumMap<>(ResourceType.class);
        EnumMap<ResourceType, Integer> recipientFinal = new EnumMap<>(ResourceType.class);
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) {
            int first = Math.addExact(offerer.amountOf(type) - offered.amountOf(type),
                    requested.amountOf(type));
            int second = Math.addExact(recipient.amountOf(type) - requested.amountOf(type),
                    offered.amountOf(type));
            if (first > offerer.capacityOf(type) || second > recipient.capacityOf(type)) {
                throw new IllegalStateException("Trade exceeds storage capacity");
            }
            offererFinal.put(type, first);
            recipientFinal.put(type, second);
        }
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) {
            offerer.amounts.put(type, offererFinal.get(type));
            recipient.amounts.put(type, recipientFinal.get(type));
            offerer.reserved.put(type,
                    offerer.reservedAmountOf(type) - offered.amountOf(type));
        }
    }

    public void debit(ResourceBundle cost) {
        if (!canAfford(cost)) {
            throw new IllegalStateException("Insufficient resources for cost " + cost);
        }
        for (ResourceType type : ResourceType.values()) {
            amounts.put(type, amountOf(type) - cost.amountOf(type));
        }
    }

    /** Debits atomically and reports failure without mutating the stockpile. */
    public boolean tryDebit(ResourceBundle cost) {
        if (!canAfford(cost)) return false;
        debit(cost);
        return true;
    }

    public boolean canAccept(ResourceBundle income) {
        Objects.requireNonNull(income, "income");
        return income.asMap().entrySet().stream()
                .allMatch(entry -> entry.getValue() <= capacityOf(entry.getKey()) - amountOf(entry.getKey()));
    }

    /** Credits the entire bundle atomically, or returns false without mutation. */
    public boolean creditExact(ResourceBundle income) {
        if (!canAccept(income)) return false;
        ResourceBundle overflow = credit(income);
        if (!overflow.isZero()) throw new IllegalStateException("Exact credit invariant violated");
        return true;
    }

    /**
     * Credits up to capacity and returns the unaccepted overflow.
     */
    public ResourceBundle credit(ResourceBundle income) {
        Objects.requireNonNull(income, "income");
        EnumMap<ResourceType, Integer> overflow = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            int availableSpace = capacityOf(type) - amountOf(type);
            int accepted = Math.min(availableSpace, income.amountOf(type));
            amounts.put(type, amountOf(type) + accepted);
            overflow.put(type, income.amountOf(type) - accepted);
        }
        return new ResourceBundle(overflow);
    }

    public void increaseCapacity(ResourceBundle increments) {
        Objects.requireNonNull(increments, "increments");
        for (ResourceType type : ResourceType.values()) {
            capacities.put(type, Math.addExact(capacityOf(type), increments.amountOf(type)));
        }
    }

    /**
     * Atomically exchanges two distinct resource types. No debit occurs unless the full
     * destination amount fits, so trade never creates implicit overflow loss.
     */
    public void exchangeExact(
            ResourceType source, int sourceAmount, ResourceType target, int targetAmount) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (source == target) throw new IllegalArgumentException("Trade resources must be distinct");
        if (sourceAmount <= 0 || targetAmount <= 0) {
            throw new IllegalArgumentException("Trade amounts must be positive");
        }
        // PHASE 3 - Added in Phase 3
        if (availableAmountOf(source) < sourceAmount) {
            throw new IllegalStateException("Insufficient source resources");
        }
        if (capacityOf(target) - amountOf(target) < targetAmount) {
            throw new IllegalStateException("Insufficient destination storage capacity");
        }
        amounts.put(source, amountOf(source) - sourceAmount);
        amounts.put(target, Math.addExact(amountOf(target), targetAmount));
    }

    /** Sets absolute capacities, clamps stored amounts, and returns discarded overflow. */
    public ResourceBundle resizeCapacity(ResourceBundle newCapacities) {
        Objects.requireNonNull(newCapacities, "newCapacities");
        EnumMap<ResourceType, Integer> overflow = new EnumMap<>(ResourceType.class);
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) {
            if (newCapacities.amountOf(type) < reservedAmountOf(type)) {
                throw new IllegalStateException("Capacity cannot be reduced below reserved resources");
            }
        }
        for (ResourceType type : ResourceType.values()) {
            int newCapacity = newCapacities.amountOf(type);
            int previousAmount = amountOf(type);
            capacities.put(type, newCapacity);
            amounts.put(type, Math.min(previousAmount, newCapacity));
            overflow.put(type, Math.max(0, previousAmount - newCapacity));
        }
        return new ResourceBundle(overflow);
    }

    // PHASE 3 - Added in Phase 3
    private void requireReservation(ResourceBundle resources) {
        Objects.requireNonNull(resources, "resources");
        if (resources.isZero()) throw new IllegalArgumentException("Reserved resources must be positive");
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) {
            if (reservedAmountOf(type) < resources.amountOf(type)) {
                throw new IllegalStateException("Trade reservation is no longer available");
            }
        }
    }

    // PHASE 3 - Added in Phase 3
    private static EnumMap<ResourceType, Integer> emptyResourceMap() {
        EnumMap<ResourceType, Integer> values = new EnumMap<>(ResourceType.class);
        // PHASE 3 - Added in Phase 3
        for (ResourceType type : ResourceType.values()) values.put(type, 0);
        return values;
    }

    // PHASE 3 - Added in Phase 3
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        // PHASE 3 - Added in Phase 3
        if (reserved == null) reserved = emptyResourceMap();
        else for (ResourceType type : ResourceType.values()) reserved.putIfAbsent(type, 0);
    }

}
