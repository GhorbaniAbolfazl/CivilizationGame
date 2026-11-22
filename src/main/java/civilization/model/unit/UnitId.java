package civilization.model.unit;

import java.util.Objects;
import java.util.UUID;

public record UnitId(UUID value) implements java.io.Serializable {
    public UnitId {
        Objects.requireNonNull(value, "value");
    }

    public static UnitId random() {
        return new UnitId(UUID.randomUUID());
    }

    public static UnitId from(civilization.model.random.GameplayRandom random) {
        Objects.requireNonNull(random, "random");
        return new UnitId(new UUID(random.nextLong(), random.nextLong()));
    }
}
