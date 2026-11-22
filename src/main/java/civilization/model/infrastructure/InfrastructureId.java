package civilization.model.infrastructure;

import java.util.Objects;
import java.util.UUID;

public record InfrastructureId(UUID value) implements java.io.Serializable {
    public InfrastructureId { Objects.requireNonNull(value, "value"); }
    public static InfrastructureId random() { return new InfrastructureId(UUID.randomUUID()); }
    public static InfrastructureId from(civilization.model.random.GameplayRandom random) {
        Objects.requireNonNull(random, "random");
        return new InfrastructureId(new UUID(random.nextLong(), random.nextLong()));
    }
}
