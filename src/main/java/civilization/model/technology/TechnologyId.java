package civilization.model.technology;

import java.util.Objects;

/** Stable identifier; a record avoids coupling persisted progress to enum ordinals. */
public record TechnologyId(String value) implements Comparable<TechnologyId>, java.io.Serializable {
    public static final TechnologyId NAVIGATION = new TechnologyId("navigation");
    public static final TechnologyId STEEL_TOOLS = new TechnologyId("steel-tools");
    public static final TechnologyId DEFENSIVE_ARCHITECTURE =
            new TechnologyId("defensive-architecture");

    public TechnologyId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("Technology ID cannot be blank");
    }

    @Override public int compareTo(TechnologyId other) { return value.compareTo(other.value); }
}
