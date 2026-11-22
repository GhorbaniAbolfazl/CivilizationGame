package civilization.model.world;

import java.util.Objects;
import java.util.UUID;

public record WildAnimalId(UUID value) implements java.io.Serializable {
    public WildAnimalId { Objects.requireNonNull(value, "value"); }
    public static WildAnimalId random() { return new WildAnimalId(UUID.randomUUID()); }
}
