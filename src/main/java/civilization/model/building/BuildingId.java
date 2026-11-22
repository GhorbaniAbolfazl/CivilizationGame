package civilization.model.building;

import java.util.Objects;
import java.util.UUID;

public record BuildingId(UUID value) implements java.io.Serializable {
    public BuildingId {
        Objects.requireNonNull(value, "value");
    }

    public static BuildingId random() {
        return new BuildingId(UUID.randomUUID());
    }

    public static BuildingId from(civilization.model.random.GameplayRandom random) {
        Objects.requireNonNull(random, "random");
        return new BuildingId(new UUID(random.nextLong(), random.nextLong()));
    }
}
