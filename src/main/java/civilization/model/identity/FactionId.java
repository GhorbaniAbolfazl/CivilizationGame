package civilization.model.identity;

import java.util.Objects;

/** Stable owner identity used independently of which collection stores an entity. */
public record FactionId(String value) implements Comparable<FactionId>, java.io.Serializable {
    public static final FactionId PLAYER = new FactionId("player");
    public static final FactionId BARBARIANS = new FactionId("barbarians");
    public static final FactionId WILD_ANIMALS = new FactionId("wild-animals");

    public FactionId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Faction ID cannot be blank");
        }
    }

    @Override
    public int compareTo(FactionId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
