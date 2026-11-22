package civilization.model.economy;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class ResourceBundle implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public static final ResourceBundle ZERO = new ResourceBundle(Map.of());

    private final Map<ResourceType, Integer> amounts;

    public ResourceBundle(Map<ResourceType, Integer> amounts) {
        Objects.requireNonNull(amounts, "amounts");
        EnumMap<ResourceType, Integer> normalized = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            int amount = amounts.getOrDefault(type, 0);
            if (amount < 0) {
                throw new IllegalArgumentException("Resource amount cannot be negative: " + type);
            }
            normalized.put(type, amount);
        }
        this.amounts = Map.copyOf(normalized);
    }

    public static ResourceBundle of(int food, int wood, int stone, int iron) {
        return new ResourceBundle(Map.of(
                ResourceType.FOOD, food,
                ResourceType.WOOD, wood,
                ResourceType.STONE, stone,
                ResourceType.IRON, iron));
    }

    public static ResourceBundle of(ResourceType type, int amount) {
        return new ResourceBundle(Map.of(Objects.requireNonNull(type, "type"), amount));
    }

    public int amountOf(ResourceType type) {
        return amounts.get(Objects.requireNonNull(type, "type"));
    }

    public Map<ResourceType, Integer> asMap() {
        return amounts;
    }

    public boolean isZero() {
        return amounts.values().stream().allMatch(amount -> amount == 0);
    }

    public ResourceBundle plus(ResourceBundle other) {
        Objects.requireNonNull(other, "other");
        EnumMap<ResourceType, Integer> result = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            result.put(type, Math.addExact(amountOf(type), other.amountOf(type)));
        }
        return new ResourceBundle(result);
    }

    public ResourceBundle minusFloorZero(ResourceBundle other) {
        Objects.requireNonNull(other, "other");
        EnumMap<ResourceType, Integer> result = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            result.put(type, Math.max(0, amountOf(type) - other.amountOf(type)));
        }
        return new ResourceBundle(result);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ResourceBundle bundle && amounts.equals(bundle.amounts);
    }

    @Override
    public int hashCode() {
        return amounts.hashCode();
    }

    @Override
    public String toString() {
        return amounts.toString();
    }
}
