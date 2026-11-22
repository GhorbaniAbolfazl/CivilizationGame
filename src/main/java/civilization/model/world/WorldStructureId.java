package civilization.model.world;

import java.util.Objects;
import java.util.UUID;

public record WorldStructureId(UUID value) implements java.io.Serializable {
    public WorldStructureId { Objects.requireNonNull(value, "value"); }
    public static WorldStructureId random() { return new WorldStructureId(UUID.randomUUID()); }
}
